<?php

namespace App\Services;

use App\Enums\TransactionType;
use App\Models\Transaction;
use App\Models\User;
use App\Support\Money;
use Dompdf\Dompdf;
use Dompdf\Options;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\View;
use Symfony\Component\HttpFoundation\StreamedResponse;

/**
 * CSV and PDF exports, plus a full JSON backup of a user's records.
 */
class ExportService
{
    public function __construct(
        private readonly ReportService $reports,
        private readonly TransactionQuery $query,
    ) {}

    /**
     * Stream the transaction ledger as CSV so a long history never has to be
     * held in memory.
     *
     * @param  array<string, mixed>  $filters
     */
    public function transactionsCsv(User $user, array $filters = []): StreamedResponse
    {
        $filename = sprintf('transactions-%s.csv', now()->format('Y-m-d'));

        return response()->streamDownload(function () use ($user, $filters): void {
            $handle = fopen('php://output', 'wb');

            fputcsv($handle, [
                'Date', 'Type', 'Amount', 'Currency', 'Account', 'Transfer account',
                'Category', 'Group', 'Merchant', 'Note', 'Tags', 'Split of', 'Receipt', 'Created via',
            ]);

            $remaining = (int) config('budget.exports.max_rows');

            $this->query->build($user, $filters + ['collapse_transfers' => false])
                ->with(['account', 'transferAccount', 'category.group', 'tags', 'splits.category'])
                ->chunkById(500, function ($transactions) use ($handle, &$remaining): bool {
                    foreach ($transactions as $transaction) {
                        foreach ($this->rowsFor($transaction) as $row) {
                            fputcsv($handle, $row);

                            if (--$remaining <= 0) {
                                return false;
                            }
                        }
                    }

                    return true;
                });

            fclose($handle);
        }, $filename, [
            'Content-Type' => 'text/csv; charset=UTF-8',
        ]);
    }

    /**
     * A printable monthly report: cash flow, category breakdown, top
     * merchants and budget progress.
     */
    public function reportPdf(User $user, Carbon $from, Carbon $to): string
    {
        $budget = app(BudgetService::class)->forMonth($user, $from);

        $html = View::make('exports.report', [
            'user' => $user,
            'from' => $from,
            'to' => $to,
            'cashFlow' => $this->reports->cashFlow($user, $from, $to),
            'categories' => $this->reports->byCategory($user, $from, $to),
            'income' => $this->reports->byCategory($user, $from, $to, TransactionType::Income),
            'merchants' => $this->reports->topMerchants($user, $from, $to),
            'balances' => $this->reports->balances($user),
            'budget' => $budget === null ? null : app(BudgetService::class)->progress($user, $budget),
            'generatedAt' => now(),
        ])->render();

        $options = new Options;
        $options->set('isRemoteEnabled', false);
        $options->set('defaultFont', 'DejaVu Sans');

        $dompdf = new Dompdf($options);
        $dompdf->loadHtml($html, 'UTF-8');
        $dompdf->setPaper(config('budget.exports.paper'), 'portrait');
        $dompdf->render();

        return (string) $dompdf->output();
    }

    /**
     * Everything the user has entered, in a form that can be re-imported.
     *
     * @return array<string, mixed>
     */
    public function backup(User $user): array
    {
        return [
            'format' => 'budget-tracker/backup',
            'version' => 1,
            'exported_at' => now()->toIso8601String(),
            'user' => [
                'name' => $user->name,
                'email' => $user->email,
                'currency_code' => $user->currency_code,
                'locale' => $user->locale,
                'timezone' => $user->timezone,
            ],
            'accounts' => $user->accounts()->get([
                'id', 'name', 'type', 'currency_code', 'opening_balance_minor',
                'current_balance_minor', 'credit_limit_minor', 'institution',
                'color', 'icon', 'is_archived', 'sort_order',
            ])->toArray(),
            'category_groups' => $user->categoryGroups()->get(['id', 'name', 'type', 'color', 'icon', 'sort_order'])->toArray(),
            'categories' => $user->categories()->get([
                'id', 'category_group_id', 'parent_id', 'name', 'type', 'color',
                'icon', 'keywords', 'sort_order', 'archived_at',
            ])->toArray(),
            'tags' => $user->tags()->get(['id', 'name', 'color'])->toArray(),
            'transactions' => $user->transactions()
                ->with(['splits', 'tags'])
                ->get([
                    'id', 'account_id', 'transfer_account_id', 'category_id', 'type',
                    'direction', 'amount_minor', 'currency_code', 'occurred_on',
                    'merchant', 'note', 'transfer_group', 'receipt_id', 'bill_id',
                    'savings_goal_id', 'recurring_rule_id', 'has_splits', 'created_via',
                ])
                ->toArray(),
            'receipts' => $user->receipts()->with('items')->get([
                'id', 'image_path', 'merchant', 'receipt_date', 'currency_code',
                'subtotal_minor', 'tax_minor', 'total_minor', 'payment_method', 'status',
            ])->toArray(),
            'budgets' => $user->budgets()->with('categories')->get()->toArray(),
            'bills' => $user->bills()->with('payments')->get()->toArray(),
            'savings_goals' => $user->savingsGoals()->with('contributions')->get()->toArray(),
            'recurring_rules' => $user->recurringRules()->get()->toArray(),
        ];
    }

    /**
     * One CSV row per transaction, or one per split when the transaction is
     * divided across categories.
     *
     * @return array<int, array<int, string>>
     */
    private function rowsFor(Transaction $transaction): array
    {
        $base = [
            $transaction->occurred_on->toDateString(),
            $transaction->type->value,
            null,
            $transaction->currency_code,
            $transaction->account?->name ?? '',
            $transaction->transferAccount?->name ?? '',
            null,
            null,
            $transaction->merchant ?? '',
            $transaction->note ?? '',
            $transaction->tags->pluck('name')->implode(', '),
            '',
            $transaction->receipt_id === null ? '' : 'yes',
            $transaction->created_via->value,
        ];

        $signed = fn (int $minor): string => number_format(
            ($minor * $transaction->direction->sign()) / (10 ** Money::exponent($transaction->currency_code)),
            Money::exponent($transaction->currency_code),
            '.',
            '',
        );

        if (! $transaction->has_splits || $transaction->splits->isEmpty()) {
            $row = $base;
            $row[2] = $signed($transaction->amount_minor);
            $row[6] = $transaction->category?->name ?? '';
            $row[7] = $transaction->category?->group?->name ?? '';

            return [$row];
        }

        return $transaction->splits->map(function ($split) use ($base, $signed, $transaction): array {
            $row = $base;
            $row[2] = $signed($split->amount_minor);
            $row[6] = $split->category?->name ?? '';
            $row[7] = $split->category?->group?->name ?? '';
            $row[11] = (string) $transaction->getKey();

            return $row;
        })->all();
    }
}
