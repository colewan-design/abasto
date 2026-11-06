<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\StoreRecurringRuleRequest;
use App\Http\Resources\V1\RecurringRuleResource;
use App\Http\Resources\V1\TransactionResource;
use App\Models\RecurringRule;
use App\Services\RecurringRuleRunner;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

/**
 * Repeating transactions: salary, rent, subscriptions, installments.
 */
class RecurringRuleController extends Controller
{
    public function __construct(
        private readonly RecurringRuleRunner $runner,
    ) {}

    public function index(Request $request): AnonymousResourceCollection
    {
        $rules = $request->user()->recurringRules()
            ->with(['account', 'category'])
            ->when(! $request->boolean('include_inactive'), fn ($query) => $query->where('is_active', true))
            ->orderBy('next_run_on')
            ->get();

        return RecurringRuleResource::collection($rules);
    }

    public function store(StoreRecurringRuleRequest $request): JsonResponse
    {
        $user = $request->user();

        $rule = $user->recurringRules()->create([
            ...$request->validated(),
            'currency_code' => $user->currency_code,
        ]);

        $this->runner->scheduleFirstRun($rule);

        return (new RecurringRuleResource($rule->refresh()->load(['account', 'category'])))
            ->response()
            ->setStatusCode(201);
    }

    public function show(RecurringRule $recurringRule): RecurringRuleResource
    {
        return new RecurringRuleResource($recurringRule->load(['account', 'category']));
    }

    public function update(StoreRecurringRuleRequest $request, RecurringRule $recurringRule): RecurringRuleResource
    {
        $recurringRule->update($request->validated());

        if ($request->has('start_date') || $request->has('frequency') || $request->has('by_month_day')) {
            $this->runner->scheduleFirstRun($recurringRule);
        }

        return new RecurringRuleResource($recurringRule->refresh()->load(['account', 'category']));
    }

    public function destroy(RecurringRule $recurringRule): JsonResponse
    {
        $recurringRule->delete();

        return response()->json(['message' => 'Recurring transaction deleted.']);
    }

    /**
     * Post the occurrences that are due now, instead of waiting for the daily
     * scheduled run.
     */
    public function run(RecurringRule $recurringRule): JsonResponse
    {
        $created = $this->runner->run($recurringRule);

        return response()->json([
            'message' => $created === []
                ? 'Nothing was due for this rule.'
                : count($created).' '.str('transaction')->plural(count($created)).' created.',
            'transactions' => TransactionResource::collection($created),
            'rule' => new RecurringRuleResource($recurringRule->refresh()),
        ]);
    }
}
