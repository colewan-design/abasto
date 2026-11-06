<?php

namespace App\Http\Controllers\Api\V1\Auth\Concerns;

use App\Http\Resources\V1\UserResource;
use App\Models\User;
use Illuminate\Http\JsonResponse;

trait IssuesAccessTokens
{
    /**
     * Mint a personal access token and return the payload every client needs
     * after signing in.
     */
    protected function tokenResponse(User $user, string $deviceName = 'api', int $status = 200): JsonResponse
    {
        $user->markActive();

        $token = $user->createToken($deviceName, ['*'], now()->addDays(90));

        return response()->json([
            'token' => $token->plainTextToken,
            'token_type' => 'Bearer',
            'expires_at' => $token->accessToken->expires_at?->toIso8601String(),
            'user' => new UserResource($user->fresh()),
            'needs_onboarding' => ! $user->hasCompletedOnboarding(),
        ], $status);
    }

    protected function deviceName(string $fallback = 'api'): string
    {
        $name = request()->string('device_name')->trim()->toString();

        if ($name !== '') {
            return mb_substr($name, 0, 60);
        }

        return mb_substr((string) (request()->userAgent() ?: $fallback), 0, 60);
    }
}
