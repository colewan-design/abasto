<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\UpdateProfileRequest;
use App\Http\Resources\V1\UserResource;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;

class ProfileController extends Controller
{
    public function show(Request $request): UserResource
    {
        $user = $request->user();
        $user->markActive();

        return (new UserResource($user))->additional([
            'meta' => [
                'unread_notifications' => $user->unreadNotifications()->count(),
                'accounts' => $user->accounts()->active()->count(),
                'receipts_awaiting_review' => $user->receipts()->awaitingReview()->count(),
            ],
        ]);
    }

    public function update(UpdateProfileRequest $request): UserResource
    {
        $user = $request->user();
        $data = $request->validated();

        $user->fill(collect($data)->only(['name', 'currency_code', 'locale', 'timezone', 'app_lock_enabled'])->all());

        if (filled($data['password'] ?? null)) {
            $user->password = $data['password'];
        }

        if (isset($data['currency_code'])) {
            $user->currency_code = strtoupper($data['currency_code']);
        }

        $user->save();

        return new UserResource($user->refresh());
    }

    /**
     * Delete the account and everything in it.
     */
    public function destroy(Request $request): JsonResponse
    {
        $user = $request->user();

        if ($user->password !== null) {
            $validated = $request->validate(['password' => ['required', 'string']]);

            if (! Hash::check($validated['password'], $user->password)) {
                throw ValidationException::withMessages([
                    'password' => 'That password is incorrect.',
                ]);
            }
        } else {
            $request->validate([
                'confirm' => ['required', 'accepted'],
            ]);
        }

        $user->tokens()->delete();
        $user->delete();

        return response()->json(['message' => 'Your account and all of its data have been deleted.']);
    }
}
