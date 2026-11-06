<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\StoreAccountRequest;
use App\Http\Requests\V1\UpdateAccountRequest;
use App\Http\Resources\V1\AccountResource;
use App\Http\Resources\V1\TransactionResource;
use App\Models\Account;
use App\Services\AccountBalanceService;
use App\Services\ReportService;
use App\Services\TransactionQuery;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Validation\ValidationException;

class AccountController extends Controller
{
    public function __construct(
        private readonly AccountBalanceService $balances,
        private readonly ReportService $reports,
        private readonly TransactionQuery $query,
    ) {}

    public function index(Request $request): AnonymousResourceCollection
    {
        $accounts = $request->user()->accounts()
            ->when(! $request->boolean('include_archived'), fn ($query) => $query->active())
            ->withCount('transactions')
            ->orderBy('sort_order')
            ->orderBy('name')
            ->get();

        return AccountResource::collection($accounts)->additional([
            'meta' => $this->reports->balances($request->user()),
        ]);
    }

    public function store(StoreAccountRequest $request): JsonResponse
    {
        $user = $request->user();
        $data = $request->validated();

        $account = $user->accounts()->create([
            ...$data,
            'currency_code' => strtoupper($data['currency_code'] ?? $user->currency_code),
            'icon' => $data['icon'] ?? null,
        ]);

        $this->balances->recalculate($account);

        return (new AccountResource($account->refresh()))->response()->setStatusCode(201);
    }

    public function show(Request $request, Account $account): AccountResource
    {
        return (new AccountResource($account->loadCount('transactions')))->additional([
            'meta' => [
                'history' => $this->reports->accountHistory($request->user(), $account, (int) $request->integer('days', 30)),
            ],
        ]);
    }

    public function update(UpdateAccountRequest $request, Account $account): AccountResource
    {
        $account->update($request->validated());

        // The opening balance is part of the account's balance, so changing it
        // has to be folded back into the running total.
        if ($request->has('opening_balance_minor')) {
            $this->balances->recalculate($account);
        }

        return new AccountResource($account->refresh());
    }

    /**
     * Accounts with history are archived rather than deleted, so the ledger
     * they are part of stays intact.
     */
    public function destroy(Request $request, Account $account): JsonResponse
    {
        if ($account->transactions()->exists() || $account->incomingTransfers()->exists()) {
            if (! $request->boolean('force')) {
                $account->update(['is_archived' => true]);

                return response()->json([
                    'message' => 'This account has transactions, so it was archived instead of deleted.',
                    'archived' => true,
                    'account' => new AccountResource($account->refresh()),
                ]);
            }

            throw ValidationException::withMessages([
                'account' => 'Delete or move this account\'s transactions before removing it.',
            ]);
        }

        $account->delete();

        return response()->json(['message' => 'Account deleted.']);
    }

    /**
     * The account's own ledger, including both legs of any transfer that
     * touches it.
     */
    public function transactions(Request $request, Account $account): AnonymousResourceCollection
    {
        $transactions = TransactionQuery::withListRelations(
            $this->query->build($request->user(), [
                'account_id' => $account->getKey(),
                'collapse_transfers' => false,
                'from' => $request->date('from')?->toDateString(),
                'to' => $request->date('to')?->toDateString(),
            ])
        )->paginate($request->integer('per_page', 50));

        return TransactionResource::collection($transactions);
    }

    /**
     * Rebuild balances from the ledger, for the "recalculate balances" control
     * in settings.
     */
    public function recalculate(Request $request): JsonResponse
    {
        $corrections = $this->balances->recalculateAll($request->user()->getKey());

        return response()->json([
            'message' => $corrections === []
                ? 'All balances were already correct.'
                : count($corrections).' '.str('balance')->plural(count($corrections)).' corrected.',
            'corrections' => $corrections,
        ]);
    }
}
