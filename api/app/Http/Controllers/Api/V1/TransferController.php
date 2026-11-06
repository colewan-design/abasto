<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\StoreTransferRequest;
use App\Http\Resources\V1\TransactionResource;
use App\Services\TransactionService;
use Illuminate\Http\JsonResponse;

/**
 * Moving money between the user's own accounts.
 *
 * A transfer is written as a pair of rows sharing a `transfer_group`, so both
 * account balances move and neither side is counted as income or expense.
 */
class TransferController extends Controller
{
    public function __construct(
        private readonly TransactionService $transactions,
    ) {}

    public function store(StoreTransferRequest $request): JsonResponse
    {
        $legs = $this->transactions->createTransfer($request->user(), $request->validated());

        return response()->json([
            'data' => new TransactionResource($legs['outflow']->load(['account', 'transferAccount'])),
            'legs' => [
                'outflow' => new TransactionResource($legs['outflow']),
                'inflow' => new TransactionResource($legs['inflow']),
            ],
        ], 201);
    }
}
