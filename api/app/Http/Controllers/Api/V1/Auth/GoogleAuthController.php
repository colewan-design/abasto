<?php

namespace App\Http\Controllers\Api\V1\Auth;

use App\Http\Controllers\Api\V1\Auth\Concerns\IssuesAccessTokens;
use App\Http\Controllers\Controller;
use App\Models\User;
use App\Services\GoogleTokenVerifier;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * Sign in with Google.
 *
 * Clients obtain an ID token from Google (Credential Manager on Android,
 * Google Identity Services on the web) and exchange it here for an API token.
 * No password is involved, and the ID token is verified server-side so a
 * client cannot claim an identity it does not hold.
 */
class GoogleAuthController extends Controller
{
    use IssuesAccessTokens;

    public function __construct(
        private readonly GoogleTokenVerifier $verifier,
    ) {}

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'id_token' => ['required', 'string'],
            'device_name' => ['nullable', 'string', 'max:60'],
            /*
             * A guest exploring the demo can sign in from inside the app. The
             * personal account always starts clean, and the sample data is
             * thrown away unless the caller asks to keep it.
             */
            'discard_demo' => ['nullable', 'boolean'],
        ]);

        $identity = $this->verifier->verify($validated['id_token']);
        $guest = $request->user();

        $user = DB::transaction(function () use ($identity): User {
            $user = User::query()->where('google_id', $identity['sub'])->first()
                ?? User::query()->where('email', $identity['email'])->whereNull('google_id')->first();

            if ($user === null) {
                return User::query()->create([
                    'name' => $identity['name'],
                    'email' => $identity['email'],
                    'google_id' => $identity['sub'],
                    'avatar_url' => $identity['picture'],
                    'currency_code' => config('budget.default_currency'),
                    'is_demo' => false,
                ])->forceFill([
                    'email_verified_at' => $identity['email_verified'] ? now() : null,
                ])->tap(fn (User $user) => $user->save());
            }

            $user->forceFill([
                'google_id' => $identity['sub'],
                'avatar_url' => $identity['picture'] ?? $user->avatar_url,
                'email_verified_at' => $user->email_verified_at ?? ($identity['email_verified'] ? now() : null),
            ])->save();

            return $user;
        });

        if ($guest !== null && $guest->isGuest() && ! $guest->is($user)) {
            $this->releaseGuest($guest, (bool) ($validated['discard_demo'] ?? true));
        }

        return $this->tokenResponse($user, $this->deviceName('google'));
    }

    /**
     * The demo account is independent of the personal one, so signing in ends
     * the guest session and (by default) removes the sample data with it.
     */
    private function releaseGuest(User $guest, bool $discard): void
    {
        $guest->tokens()->delete();

        if ($discard) {
            $guest->delete();
        }
    }
}
