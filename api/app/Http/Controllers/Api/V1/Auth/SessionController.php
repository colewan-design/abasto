<?php

namespace App\Http\Controllers\Api\V1\Auth;

use App\Http\Controllers\Api\V1\Auth\Concerns\IssuesAccessTokens;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rules\Password;
use Illuminate\Validation\ValidationException;

/**
 * Email and password sign-in, alongside Google sign-in.
 *
 * Google is the primary path in the product, but a password account keeps the
 * API usable where a Google client ID is not configured, and gives people a
 * way in if they lose access to their Google account.
 */
class SessionController extends Controller
{
    use IssuesAccessTokens;

    public function register(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'name' => ['required', 'string', 'max:120'],
            'email' => ['required', 'email', 'max:190', 'unique:users,email'],
            'password' => ['required', 'confirmed', Password::defaults()],
            'currency_code' => ['nullable', 'string', 'size:3'],
            'device_name' => ['nullable', 'string', 'max:60'],
        ]);

        $user = User::query()->create([
            'name' => $validated['name'],
            'email' => $validated['email'],
            'password' => $validated['password'],
            'currency_code' => strtoupper($validated['currency_code'] ?? (string) config('budget.default_currency')),
        ]);

        return $this->tokenResponse($user, $this->deviceName(), 201);
    }

    public function login(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'email' => ['required', 'email'],
            'password' => ['required', 'string'],
            'device_name' => ['nullable', 'string', 'max:60'],
        ]);

        $user = User::query()->where('email', $validated['email'])->first();

        if ($user === null || $user->password === null || ! Hash::check($validated['password'], $user->password)) {
            throw ValidationException::withMessages([
                'email' => 'Those credentials do not match our records.',
            ]);
        }

        return $this->tokenResponse($user, $this->deviceName());
    }

    /**
     * Sign out of this device by revoking the token that made the request.
     */
    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()?->delete();

        return response()->json(['message' => 'Signed out.']);
    }

    /**
     * Sign out everywhere, used by the "sign out of all devices" control.
     */
    public function logoutAll(Request $request): JsonResponse
    {
        $request->user()->tokens()->delete();

        return response()->json(['message' => 'Signed out of all devices.']);
    }
}
