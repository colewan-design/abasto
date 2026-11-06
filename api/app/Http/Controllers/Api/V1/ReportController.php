<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\ReportRequest;
use App\Http\Resources\V1\TransactionResource;
use App\Services\ReportService;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Carbon;

class ReportController extends Controller
{
    public function __construct(
        private readonly ReportService $reports,
    ) {}

    /**
     * Everything the home screen shows, in one request.
     */
    public function dashboard(ReportRequest $request): JsonResponse
    {
        $month = $request->filled('month')
            ? Carbon::createFromFormat('Y-m', $request->string('month')->toString())
            : today();

        $dashboard = $this->reports->dashboard($request->user(), $month);

        return response()->json([
            'data' => [
                ...$dashboard,
                'recent_transactions' => TransactionResource::collection($dashboard['recent_transactions']),
            ],
        ]);
    }

    public function cashFlow(ReportRequest $request): JsonResponse
    {
        ['from' => $from, 'to' => $to] = $request->period();

        return response()->json([
            'data' => [
                'period' => ['from' => $from->toDateString(), 'to' => $to->toDateString()],
                'totals' => $this->reports->cashFlow($request->user(), $from, $to),
                'daily' => $this->reports->dailyFlow($request->user(), $from, $to),
            ],
        ]);
    }

    public function categories(ReportRequest $request): JsonResponse
    {
        ['from' => $from, 'to' => $to] = $request->period();

        return response()->json([
            'data' => $this->reports->byCategory($request->user(), $from, $to, $request->transactionType()),
            'meta' => [
                'period' => ['from' => $from->toDateString(), 'to' => $to->toDateString()],
                'type' => $request->transactionType()->value,
            ],
        ]);
    }

    public function trends(ReportRequest $request): JsonResponse
    {
        return response()->json([
            'data' => $this->reports->monthlyTrends($request->user(), $request->integer('months', 6)),
        ]);
    }

    public function merchants(ReportRequest $request): JsonResponse
    {
        ['from' => $from, 'to' => $to] = $request->period();

        return response()->json([
            'data' => $this->reports->topMerchants($request->user(), $from, $to, $request->integer('limit', 10)),
            'meta' => ['period' => ['from' => $from->toDateString(), 'to' => $to->toDateString()]],
        ]);
    }
}
