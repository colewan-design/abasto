<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Symfony\Component\HttpFoundation\Response;

/**
 * Resolves the authenticated user when a token is present, without rejecting
 * the request when it is not.
 *
 * Two routes need this. Google sign-in may be called while exploring as a
 * guest, and knowing about that guest session is what lets the API retire it.
 * Receipt images are opened by the browser from a signed URL that carries no
 * bearer token, and when a token *is* present the owner scope still applies.
 */
class OptionalAuthentication
{
    public function handle(Request $request, Closure $next, string ...$guards): Response
    {
        foreach ($guards === [] ? [null] : $guards as $guard) {
            if (Auth::guard($guard)->check()) {
                Auth::shouldUse($guard);

                break;
            }
        }

        return $next($request);
    }
}
