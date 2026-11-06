<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\CompleteOnboardingRequest;
use App\Http\Resources\V1\AccountResource;
use App\Http\Resources\V1\BudgetResource;
use App\Http\Resources\V1\UserResource;
use App\Services\OnboardingService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Guided setup for a new account: currency, accounts and starting balances,
 * the starter categories, and a first monthly budget.
 */
class OnboardingController extends Controller
{
    public function __construct(
        private readonly OnboardingService $onboarding,
    ) {}

    /**
     * The choices the wizard presents, so the client does not hardcode them.
     */
    public function options(Request $request): JsonResponse
    {
        return response()->json([
            'data' => [
                ...$this->onboarding->options(),
                'detected_currency' => $request->user()->currency_code,
                'completed' => $request->user()->hasCompletedOnboarding(),
            ],
        ]);
    }

    public function complete(CompleteOnboardingRequest $request): JsonResponse
    {
        $result = $this->onboarding->complete($request->user(), $request->validated());

        return response()->json([
            'message' => "You're all set.",
            'user' => new UserResource($result['user']),
            'accounts' => AccountResource::collection($result['accounts']),
            'budget' => $result['budget'] === null ? null : new BudgetResource($result['budget']),
        ], 201);
    }

    /**
     * Add the starter categories without running the whole wizard, for an
     * account that skipped setup.
     */
    public function seedCategories(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'categories' => ['nullable', 'array', 'max:300'],
            'categories.*' => ['string', 'max:60'],
        ]);

        $this->onboarding->seedCategories($request->user(), $validated['categories'] ?? null);

        return response()->json(['message' => 'Categories added.']);
    }
}
