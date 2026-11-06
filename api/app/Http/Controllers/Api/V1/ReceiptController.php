<?php

namespace App\Http\Controllers\Api\V1;

use App\Enums\ReceiptStatus;
use App\Http\Controllers\Controller;
use App\Http\Requests\V1\ConfirmReceiptRequest;
use App\Http\Requests\V1\StoreReceiptRequest;
use App\Http\Resources\V1\ReceiptResource;
use App\Http\Resources\V1\TransactionResource;
use App\Jobs\ProcessReceipt;
use App\Models\Receipt;
use App\Services\CategorySuggester;
use App\Services\Receipts\ReceiptConfirmer;
use App\Services\Receipts\ReceiptProcessor;
use App\Services\Receipts\ReceiptStorage;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

/**
 * Receipt capture and review.
 *
 * Upload returns immediately with a pending receipt and queues extraction, so
 * the camera screen is never blocked on OCR. The client then polls (or
 * re-reads) the receipt to get the draft, corrects it, and confirms — which is
 * the point the expense is actually written.
 */
class ReceiptController extends Controller
{
    public function __construct(
        private readonly ReceiptStorage $storage,
        private readonly ReceiptProcessor $processor,
        private readonly ReceiptConfirmer $confirmer,
        private readonly CategorySuggester $suggester,
    ) {}

    public function index(Request $request): AnonymousResourceCollection
    {
        $receipts = $request->user()->receipts()
            ->withCount('items')
            ->when($request->filled('status'), fn ($query) => $query->where('status', $request->string('status')->toString()))
            ->when($request->boolean('awaiting_review'), fn ($query) => $query->awaitingReview())
            ->orderByDesc('created_at')
            ->paginate($request->integer('per_page', 25));

        return ReceiptResource::collection($receipts);
    }

    public function store(StoreReceiptRequest $request): JsonResponse
    {
        $user = $request->user();
        $stored = $this->storage->store($user, $request->file('image'));

        $receipt = $user->receipts()->create([
            'image_path' => $stored['image_path'],
            'thumbnail_path' => $stored['thumbnail_path'],
            'original_filename' => $stored['original_filename'],
            'mime_type' => $stored['mime_type'],
            'size_bytes' => $stored['size_bytes'],
            'status' => ReceiptStatus::Pending->value,
            'currency_code' => $user->currency_code,
            'raw_text' => $request->input('recognized_text'),
            'is_demo' => $user->is_demo,
        ]);

        $receipt->forceFill(['content_hash' => $stored['content_hash']])->saveQuietly();

        // Running inline when requested keeps the flow simple for clients that
        // would rather wait for the draft than poll for it.
        if ($request->boolean('process')) {
            $this->processor->process($receipt);
        } else {
            ProcessReceipt::dispatch($receipt->getKey());
        }

        return (new ReceiptResource($this->load($receipt->refresh())))
            ->response()
            ->setStatusCode(201);
    }

    public function show(Request $request, Receipt $receipt): ReceiptResource
    {
        $receipt = $this->load($receipt);

        return (new ReceiptResource($receipt))->additional([
            'meta' => [
                'suggested_category' => $this->suggester->suggestForMerchant($request->user(), $receipt->merchant),
                'suggested_split' => $this->splitPreview($receipt),
            ],
        ]);
    }

    /**
     * Re-run extraction, for a receipt that failed or was captured before a
     * better OCR driver was configured.
     */
    public function reprocess(Receipt $receipt): ReceiptResource
    {
        return new ReceiptResource($this->load($this->processor->process($receipt)));
    }

    /**
     * Save the reviewed receipt as an expense.
     */
    public function confirm(ConfirmReceiptRequest $request, Receipt $receipt): JsonResponse
    {
        $transaction = $this->confirmer->confirm($request->user(), $receipt, $request->validated());

        return response()->json([
            'message' => 'Receipt saved.',
            'transaction' => new TransactionResource($transaction->load(['account', 'category', 'splits.category', 'tags'])),
            'receipt' => new ReceiptResource($this->load($receipt->refresh())),
        ], 201);
    }

    public function destroy(Receipt $receipt): JsonResponse
    {
        $this->storage->delete($receipt);
        $receipt->forceDelete();

        return response()->json(['message' => 'Receipt deleted.']);
    }

    private function load(Receipt $receipt): Receipt
    {
        return $receipt->load([
            'items.category.group',
            'items.suggestedCategory.group',
            'transactions',
        ]);
    }

    /**
     * How the receipt would be split across categories if confirmed as-is,
     * shown on the review screen before the user commits.
     *
     * @return array<int, array{category_id: int|null, amount_minor: int, items: int}>
     */
    private function splitPreview(Receipt $receipt): array
    {
        if ($receipt->items->isEmpty()) {
            return [];
        }

        return $receipt->items
            ->groupBy(fn ($item) => (string) ($item->effectiveCategoryId() ?? ''))
            ->map(fn ($items, string $categoryId): array => [
                'category_id' => $categoryId === '' ? null : (int) $categoryId,
                'amount_minor' => (int) $items->sum('total_minor'),
                'items' => $items->count(),
            ])
            ->values()
            ->all();
    }
}
