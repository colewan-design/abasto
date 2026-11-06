<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\UpdateBudgetRequest;
use App\Http\Resources\V1\BudgetResource;
use App\Http\Resources\V1\CategoryResource;
use App\Services\BudgetService;
use App\Services\CategoryLedger;
use App\Services\OnboardingService;
use App\Support\Money;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;

class BudgetController extends Controller
{
    public function __construct(
        private readonly BudgetService $budgets,
        private readonly CategoryLedger $ledger,
        private readonly OnboardingService $onboarding,
    ) {}

    /**
     * The budget for a month with its spending progress. Months the user has
     * not set up yet come back as a null budget rather than an error, so the
     * client can offer to create one.
     */
    public function show(Request $request): JsonResponse
    {
        $request->validate(['month' => ['nullable', 'date_format:Y-m']]);

        $user = $request->user();
        $month = $this->month($request);
        $budget = $this->budgets->forMonth($user, $month);

        if ($budget === null) {
            return response()->json([
                'data' => null,
                'meta' => [
                    'month' => $month->format('Y-m'),
                    'label' => $month->format('F Y'),
                    'suggestions' => $this->onboarding->suggestBudget($user, $this->ledger),
                ],
            ]);
        }

        $progress = $this->budgets->progress($user, $budget);

        return response()->json([
            'data' => new BudgetResource($budget),
            'meta' => [
                'month' => $month->format('Y-m'),
                'label' => $month->format('F Y'),
                'overall' => [
                    ...$progress['overall'],
                    'formatted' => [
                        'limit' => $progress['overall']['limit_minor'] === null
                            ? null
                            : Money::ofMinor($progress['overall']['limit_minor'], $budget->currency_code)->format(),
                        'spent' => Money::ofMinor($progress['overall']['spent_minor'], $budget->currency_code)->format(),
                        'remaining' => $progress['overall']['remaining_minor'] === null
                            ? null
                            : Money::ofMinor($progress['overall']['remaining_minor'], $budget->currency_code)->format(),
                    ],
                ],
                'categories' => array_map(fn (array $line): array => [
                    ...$line,
                    'category' => $line['category'] === null ? null : new CategoryResource($line['category']),
                ], $progress['categories']),
                'unbudgeted_minor' => $progress['unbudgeted_minor'],
                'uncategorized_minor' => $progress['uncategorized_minor'],
            ],
        ]);
    }

    /**
     * Create or replace the limits for a month.
     */
    public function update(UpdateBudgetRequest $request): JsonResponse
    {
        $user = $request->user();
        $data = $request->validated();
        $budget = $this->budgets->ensureForMonth($user, $this->month($request));

        $budget->fill(array_filter([
            'overall_amount_minor' => $data['overall_amount_minor'] ?? null,
            'rollover_enabled' => $data['rollover_enabled'] ?? null,
            'alert_threshold' => $data['alert_threshold'] ?? null,
        ], fn ($value): bool => $value !== null));

        if (array_key_exists('overall_amount_minor', $data)) {
            $budget->overall_amount_minor = $data['overall_amount_minor'];
        }

        $budget->save();

        if (array_key_exists('categories', $data)) {
            $this->budgets->setCategoryLimits($budget, $data['categories']);
        }

        return $this->show($request);
    }

    /**
     * Suggested limits based on the last three months of actual spending.
     */
    public function suggestions(Request $request): JsonResponse
    {
        return response()->json([
            'data' => $this->onboarding->suggestBudget($request->user(), $this->ledger),
        ]);
    }

    private function month(Request $request): Carbon
    {
        return $request->filled('month')
            ? Carbon::createFromFormat('Y-m', $request->string('month')->toString())->startOfMonth()
            : today()->startOfMonth();
    }
}
