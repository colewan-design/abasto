<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\IndexTransactionRequest;
use App\Http\Requests\V1\ReportRequest;
use App\Services\ExportService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Symfony\Component\HttpFoundation\StreamedResponse;

class ExportController extends Controller
{
    public function __construct(
        private readonly ExportService $exports,
    ) {}

    /**
     * The filtered ledger as CSV. Accepts the same filters as the transaction
     * list, so "export what I'm looking at" is a single call.
     */
    public function transactions(IndexTransactionRequest $request): StreamedResponse
    {
        return $this->exports->transactionsCsv($request->user(), $request->filters());
    }

    public function report(ReportRequest $request): Response
    {
        ['from' => $from, 'to' => $to] = $request->period();

        $pdf = $this->exports->reportPdf($request->user(), $from, $to);

        return response($pdf, 200, [
            'Content-Type' => 'application/pdf',
            'Content-Disposition' => sprintf(
                'attachment; filename="budget-report-%s.pdf"',
                $from->format('Y-m'),
            ),
        ]);
    }

    /**
     * A full JSON backup of the account's records.
     */
    public function backup(Request $request): JsonResponse
    {
        return response()->json($this->exports->backup($request->user()), 200, [
            'Content-Disposition' => sprintf(
                'attachment; filename="budget-backup-%s.json"',
                now()->format('Y-m-d'),
            ),
        ], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
    }
}
