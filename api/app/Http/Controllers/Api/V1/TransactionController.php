<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\IndexTransactionRequest;
use App\Http\Requests\V1\StoreTransactionRequest;
use App\Http\Requests\V1\UpdateTransactionRequest;
use App\Http\Resources\V1\TransactionResource;
use App\Models\Transaction;
use App\Services\TransactionQuery;
use App\Services\TransactionService;
use App\Support\Money;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

class TransactionController extends Controller
{
    public function __construct(
        private readonly TransactionService $transactions,
        private readonly TransactionQuery $query,
    ) {}

    /**
     * Search and filter the ledger. The response carries totals for the
     * current filter set so the figures above the list always match it.
     */
    public function index(IndexTransactionRequest $request): AnonymousResourceCollection
    {
        $user = $request->user();
        $filters = $request->filters();

        $transactions = TransactionQuery::withListRelations($this->query->build($user, $filters))
            ->paginate($request->integer('per_page', 50))
            ->withQueryString();

        $totals = $this->query->totals($user, $filters);

        return TransactionResource::collection($transactions)->additional([
            'meta' => [
                'totals' => [
                    ...$totals,
                    'formatted' => [
                        'income' => Money::ofMinor($totals['income_minor'], $user->currency_code)->format(),
                        'expense' => Money::ofMinor($totals['expense_minor'], $user->currency_code)->format(),
                        'net' => Money::ofMinor($totals['net_minor'], $user->currency_code)->format(),
                    ],
                ],
                'filters' => $filters,
            ],
        ]);
    }

    public function store(StoreTransactionRequest $request): JsonResponse
    {
        $transaction = $this->transactions->create($request->user(), $request->validated());

        return (new TransactionResource($this->load($transaction)))->response()->setStatusCode(201);
    }

    public function show(Transaction $transaction): TransactionResource
    {
        return new TransactionResource($this->load($transaction));
    }

    public function update(UpdateTransactionRequest $request, Transaction $transaction): TransactionResource
    {
        $updated = $this->transactions->update($transaction, $request->validated());

        return new TransactionResource($this->load($updated));
    }

    /**
     * Deleting one leg of a transfer removes both, so a transfer can never be
     * left half-recorded.
     */
    public function destroy(Transaction $transaction): JsonResponse
    {
        $this->transactions->delete($transaction);

        return response()->json(['message' => 'Transaction deleted.']);
    }

    private function load(Transaction $transaction): Transaction
    {
        return $transaction->load([
            'account', 'transferAccount', 'category.group', 'tags',
            'splits.category', 'receipt',
        ]);
    }
}
