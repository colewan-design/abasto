<?php

namespace App\Http\Resources\V1;

use App\Models\User;
use App\Support\Money;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin User
 */
class UserResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'email' => $this->email,
            'avatar_url' => $this->avatar_url,
            'currency' => [
                'code' => $this->currency_code,
                'symbol' => Money::symbol($this->currency_code),
                'exponent' => Money::exponent($this->currency_code),
            ],
            'locale' => $this->locale,
            'timezone' => $this->timezone,
            'is_guest' => $this->isGuest(),
            'has_password' => $this->password !== null,
            'is_google_linked' => $this->google_id !== null,
            'onboarding_completed' => $this->hasCompletedOnboarding(),
            'onboarding_completed_at' => $this->onboarding_completed_at?->toIso8601String(),
            'app_lock_enabled' => (bool) $this->app_lock_enabled,
            'unread_notifications' => $this->whenCounted('notifications'),
            'created_at' => $this->created_at?->toIso8601String(),
        ];
    }
}
