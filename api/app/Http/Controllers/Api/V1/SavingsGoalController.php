<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\StoreContributionRequest;
use App\Http\Requests\V1\StoreSavingsGoalRequest;
use App\Http\Resources\V1\SavingsContributionResource;
use App\Http\Resources\V1\SavingsGoalResource;
use App\Models\SavingsContribution;
use App\Models\SavingsGoal;
use App\Services\SavingsGoalService;
use App\Support\Money;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

class SavingsGoalController extends Controller
{
    public function __construct(
        private readonly SavingsGoalService $goals,
    ) {}

    public function index(Request $request): AnonymousResourceCollection
    {
        $user = $request->user();

        $goals = $user->savingsGoals()
            ->with('account')
            ->withCount('contributions')
            ->when(! $request->boolean('include_archived'), fn ($query) => $query->active())
            ->orderBy('sort_order')
            ->orderBy('name')
            ->get();

        return SavingsGoalResource::collection($goals)->additional([
            'meta' => [
                'total_target_minor' => (int) $goals->sum('target_amount_minor'),
                'total_saved_minor' => (int) $goals->sum('current_amount_minor'),
                'formatted' => [
                    'total_target' => Money::ofMinor((int) $goals->sum('target_amount_minor'), $user->currency_code)->format(),
                    'total_saved' => Money::ofMinor((int) $goals->sum('current_amount_minor'), $user->currency_code)->format(),
                ],
            ],
        ]);
    }

    public function store(StoreSavingsGoalRequest $request): JsonResponse
    {
        $user = $request->user();

        $goal = $user->savingsGoals()->create([
            ...$request->validated(),
            'currency_code' => $user->currency_code,
        ]);

        return (new SavingsGoalResource($goal->load('account')))->response()->setStatusCode(201);
    }

    public function show(SavingsGoal $savingsGoal): SavingsGoalResource
    {
        return new SavingsGoalResource($savingsGoal->load([
            'account',
            'contributions' => fn ($query) => $query->orderByDesc('contributed_on'),
        ]));
    }

    public function update(StoreSavingsGoalRequest $request, SavingsGoal $savingsGoal): SavingsGoalResource
    {
        $savingsGoal->update($request->validated());
        $this->goals->recalculate($savingsGoal);

        return new SavingsGoalResource($savingsGoal->refresh()->load('account'));
    }

    public function destroy(SavingsGoal $savingsGoal): JsonResponse
    {
        $savingsGoal->delete();

        return response()->json(['message' => 'Savings goal deleted.']);
    }

    /**
     * Record money set aside for a goal. Naming a source account also moves
     * the money there, so balances and progress stay in step.
     */
    public function contribute(StoreContributionRequest $request, SavingsGoal $savingsGoal): JsonResponse
    {
        $contribution = $this->goals->contribute($request->user(), $savingsGoal, $request->validated());

        return response()->json([
            'message' => 'Contribution recorded.',
            'contribution' => new SavingsContributionResource($contribution),
            'goal' => new SavingsGoalResource($savingsGoal->refresh()),
        ], 201);
    }

    public function removeContribution(SavingsGoal $savingsGoal, SavingsContribution $contribution): JsonResponse
    {
        abort_unless($contribution->savings_goal_id === $savingsGoal->getKey(), 404);

        $this->goals->removeContribution($contribution);

        return response()->json([
            'message' => 'Contribution removed.',
            'goal' => new SavingsGoalResource($savingsGoal->refresh()),
        ]);
    }
}
