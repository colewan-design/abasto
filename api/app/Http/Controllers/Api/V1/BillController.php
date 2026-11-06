<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\PayBillRequest;
use App\Http\Requests\V1\StoreBillRequest;
use App\Http\Requests\V1\UpdateBillRequest;
use App\Http\Resources\V1\BillPaymentResource;
use App\Http\Resources\V1\BillResource;
use App\Models\Bill;
use App\Models\BillPayment;
use App\Services\BillService;
use App\Support\Money;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

class BillController extends Controller
{
    public function __construct(
        private readonly BillService $bills,
    ) {}

    public function index(Request $request): AnonymousResourceCollection
    {
        $user = $request->user();

        $bills = $user->bills()
            ->with(['account', 'category'])
            ->when(! $request->boolean('include_inactive'), fn ($query) => $query->active())
            ->orderBy('due_date')
            ->get();

        $dueSoon = $bills->filter(fn (Bill $bill): bool => $bill->daysUntilDue() <= 30);

        return BillResource::collection($bills)->additional([
            'meta' => [
                'due_within_30_days_minor' => (int) $dueSoon->sum('amount_minor'),
                'overdue_count' => $bills->filter(fn (Bill $bill): bool => $bill->isOverdue())->count(),
                'formatted' => [
                    'due_within_30_days' => Money::ofMinor((int) $dueSoon->sum('amount_minor'), $user->currency_code)->format(),
                ],
            ],
        ]);
    }

    /**
     * Bills due soon, for the home screen's upcoming payments card.
     */
    public function upcoming(Request $request): AnonymousResourceCollection
    {
        return BillResource::collection(
            $this->bills->upcoming($request->user(), $request->integer('days', 30))
        );
    }

    public function store(StoreBillRequest $request): JsonResponse
    {
        $user = $request->user();

        $bill = $user->bills()->create([
            ...$request->validated(),
            'currency_code' => $user->currency_code,
        ]);

        return (new BillResource($bill->load(['account', 'category'])))->response()->setStatusCode(201);
    }

    public function show(Bill $bill): BillResource
    {
        return new BillResource($bill->load([
            'account',
            'category',
            'payments' => fn ($query) => $query->orderByDesc('period_due_date')->with('transaction'),
        ]));
    }

    public function update(UpdateBillRequest $request, Bill $bill): BillResource
    {
        $bill->update($request->validated());

        return new BillResource($bill->refresh()->load(['account', 'category']));
    }

    public function destroy(Bill $bill): JsonResponse
    {
        $bill->delete();

        return response()->json(['message' => 'Bill deleted.']);
    }

    /**
     * Mark the current cycle paid. Unless told otherwise this records the
     * matching expense and links it, so the payment is never entered twice.
     */
    public function pay(PayBillRequest $request, Bill $bill): JsonResponse
    {
        $payment = $this->bills->pay($request->user(), $bill, $request->validated());

        return response()->json([
            'message' => "{$bill->name} marked as paid.",
            'payment' => new BillPaymentResource($payment->load('transaction')),
            'bill' => new BillResource($bill->refresh()->load(['account', 'category'])),
        ], 201);
    }

    public function skip(Bill $bill): JsonResponse
    {
        $payment = $this->bills->skip($bill);

        return response()->json([
            'message' => "{$bill->name} skipped for this cycle.",
            'payment' => new BillPaymentResource($payment),
            'bill' => new BillResource($bill->refresh()),
        ]);
    }

    /**
     * Undo a payment, removing the expense it created.
     */
    public function unpay(Bill $bill, BillPayment $payment): JsonResponse
    {
        abort_unless($payment->bill_id === $bill->getKey(), 404);

        $this->bills->unpay($payment);

        return response()->json([
            'message' => 'Payment removed.',
            'bill' => new BillResource($bill->refresh()),
        ]);
    }
}
