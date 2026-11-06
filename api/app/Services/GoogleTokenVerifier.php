<?php

namespace App\Services;

use Firebase\JWT\JWK;
use Firebase\JWT\JWT;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Validation\ValidationException;
use Throwable;

/**
 * Verifies a Google ID token and returns the signed-in person's identity.
 *
 * The signature is checked locally against Google's published JWKS, which is
 * cached for twelve hours so sign-in does not wait on a round trip. Google
 * rotates signing keys, so an unrecognised key id busts the cache and retries
 * once before giving up. The `tokeninfo` endpoint is the last resort when key
 * discovery is unavailable.
 *
 * Both paths enforce the issuer and the audience, so a token minted for a
 * different application cannot be replayed against this API.
 */
class GoogleTokenVerifier
{
    private const JWKS_URI = 'https://www.googleapis.com/oauth2/v3/certs';

    private const TOKENINFO_URI = 'https://oauth2.googleapis.com/tokeninfo';

    private const ISSUERS = ['accounts.google.com', 'https://accounts.google.com'];

    private const CACHE_KEY = 'google:jwks';

    /**
     * @return array{sub: string, email: string, name: string, picture: string|null, email_verified: bool}
     */
    public function verify(string $idToken): array
    {
        $audiences = $this->audiences();

        if ($audiences === []) {
            throw ValidationException::withMessages([
                'id_token' => 'Google sign-in is not configured on this server. Set GOOGLE_CLIENT_ID.',
            ]);
        }

        $claims = $this->decodeLocally($idToken, refresh: false)
            ?? $this->decodeLocally($idToken, refresh: true)
            ?? $this->decodeRemotely($idToken);

        if ($claims === null) {
            $this->reject();
        }

        $this->assertClaims($claims, $audiences);

        $email = (string) $claims['email'];

        return [
            'sub' => (string) $claims['sub'],
            'email' => $email,
            'name' => (string) ($claims['name'] ?? (strstr($email, '@', true) ?: 'Me')),
            'picture' => isset($claims['picture']) ? (string) $claims['picture'] : null,
            'email_verified' => filter_var($claims['email_verified'] ?? false, FILTER_VALIDATE_BOOLEAN),
        ];
    }

    /**
     * Accepts a comma separated list so the web, Android and iOS OAuth client
     * IDs can all mint tokens this API will trust.
     *
     * @return array<int, string>
     */
    public function audiences(): array
    {
        return collect(explode(',', (string) config('services.google.client_id')))
            ->map(fn (string $id): string => trim($id))
            ->filter()
            ->values()
            ->all();
    }

    /**
     * @return array<string, mixed>|null
     */
    private function decodeLocally(string $idToken, bool $refresh): ?array
    {
        $jwks = $this->jwks($refresh);

        if ($jwks === null) {
            return null;
        }

        try {
            return (array) JWT::decode($idToken, JWK::parseKeySet($jwks));
        } catch (Throwable) {
            return null;
        }
    }

    /**
     * @return array<string, mixed>|null
     */
    private function jwks(bool $refresh): ?array
    {
        if ($refresh) {
            Cache::forget(self::CACHE_KEY);
        }

        return Cache::remember(self::CACHE_KEY, now()->addHours(12), function (): ?array {
            try {
                $response = Http::timeout(10)->get(self::JWKS_URI);
            } catch (Throwable) {
                return null;
            }

            return $response->successful() && is_array($response->json('keys'))
                ? $response->json()
                : null;
        });
    }

    /**
     * @return array<string, mixed>|null
     */
    private function decodeRemotely(string $idToken): ?array
    {
        try {
            $response = Http::timeout(10)->get(self::TOKENINFO_URI, ['id_token' => $idToken]);
        } catch (Throwable) {
            return null;
        }

        return $response->successful() ? $response->json() : null;
    }

    /**
     * @param  array<string, mixed>  $claims
     * @param  array<int, string>  $audiences
     */
    private function assertClaims(array $claims, array $audiences): void
    {
        $valid = in_array((string) ($claims['iss'] ?? ''), self::ISSUERS, true)
            && in_array((string) ($claims['aud'] ?? ''), $audiences, true)
            && (int) ($claims['exp'] ?? 0) > time()
            && filled($claims['sub'] ?? null)
            && filled($claims['email'] ?? null);

        if (! $valid) {
            $this->reject();
        }
    }

    /**
     * @return never
     */
    private function reject(): void
    {
        throw ValidationException::withMessages([
            'id_token' => 'That Google sign-in could not be verified.',
        ]);
    }
}
