<?php

namespace App\Models\Scopes;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Scope;
use Illuminate\Support\Facades\Auth;

/**
 * Restricts every query to rows belonging to the authenticated user.
 *
 * Route model binding therefore resolves to a 404 instead of leaking another
 * person's record, and demo data can never appear in a real account. Queue
 * jobs, console commands and seeders run without an authenticated user, so the
 * scope deliberately does nothing there and those callers pass `user_id`
 * explicitly.
 */
class OwnedByCurrentUser implements Scope
{
    public function apply(Builder $builder, Model $model): void
    {
        if (! Auth::hasUser()) {
            return;
        }

        $builder->where($model->qualifyColumn('user_id'), Auth::id());
    }
}
