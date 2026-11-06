<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Records that an account is in use, at most once an hour so an active client
 * does not cause a write on every request.
 *
 * This is what `demo:prune` reads to decide which abandoned guest accounts are
 * safe to remove.
 */
class TrackUserActivity
{
    public function handle(Request $request, Closure $next): Response
    {
        $user = $request->user();

        if ($user !== null && ($user->last_active_at === null || $user->last_active_at->lt(now()->subHour()))) {
            $user->markActive();
        }

        return $next($request);
    }
}
