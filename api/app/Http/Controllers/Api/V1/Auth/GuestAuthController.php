<?php

namespace App\Http\Controllers\Api\V1\Auth;

use App\Http\Controllers\Api\V1\Auth\Concerns\IssuesAccessTokens;
use App\Http\Controllers\Controller;
use App\Models\User;
use App\Services\DemoDataBuilder;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

/**
 * "Explore as guest": creates a throwaway demo account pre-filled with sample
 * transactions, budgets, bills, receipts and savings goals.
 *
 * The guest is a real user row with `is_demo = true`, which is what keeps the
 * sample data isolated — every query is scoped by owner, so demo records can
 * never surface in a personal account.
 */
class GuestAuthController extends Controller
{
    use IssuesAccessTokens;

    public function __construct(
        private readonly DemoDataBuilder $demo,
    ) {}

    public function store(Request $request): JsonResponse
    {
        $request->validate([
            'device_name' => ['nullable', 'string', 'max:60'],
        ]);

        $handle = Str::lower(Str::random(12));

        $user = User::query()->create([
            'name' => 'Guest',
            'email' => "guest-{$handle}@demo.local",
            'currency_code' => config('budget.demo.currency'),
            'is_demo' => true,
        ]);

        $this->demo->build($user);

        return $this->tokenResponse($user->refresh(), $this->deviceName('guest'), 201);
    }
}
