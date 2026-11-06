<?php

namespace Database\Factories;

use App\Enums\DevicePlatform;
use App\Models\Device;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

/**
 * @extends Factory<Device>
 */
class DeviceFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'platform' => DevicePlatform::Android->value,
            'push_token' => Str::random(64),
            'name' => 'Pixel 8',
            'app_version' => '1.0.0',
            'last_seen_at' => now(),
        ];
    }
}
