<?php

namespace Database\Seeders;

use App\Models\User;
use App\Services\DemoDataBuilder;
use Illuminate\Database\Seeder;
use Illuminate\Support\Str;

class DatabaseSeeder extends Seeder
{
    public function __construct(
        private readonly DemoDataBuilder $demo,
    ) {}

    /**
     * Seeds two accounts for local development: a password account that has
     * been through guided setup, and a guest account full of sample data so
     * every screen has something to show.
     */
    public function run(): void
    {
        $user = User::query()->firstOrCreate(
            ['email' => 'dev@budget.test'],
            [
                'name' => 'Dev User',
                'password' => 'password',
                'currency_code' => config('budget.default_currency'),
                'email_verified_at' => now(),
            ],
        );

        if ($user->accounts()->doesntExist()) {
            $this->demo->build($user);
            $user->forceFill(['is_demo' => false])->save();

            // Sample rows are stamped `is_demo` by the builder; this account is
            // a real one, so clear the flag on its data too.
            $user->transactions()->update(['is_demo' => false]);
            $user->receipts()->update(['is_demo' => false]);
        }

        $guest = User::query()->firstOrCreate(
            ['email' => 'guest-seed@demo.local'],
            [
                'name' => 'Guest',
                'currency_code' => config('budget.demo.currency'),
                'is_demo' => true,
            ],
        );

        if ($guest->accounts()->doesntExist()) {
            $this->demo->build($guest);
        }

        $this->command?->info('Signed-in account:  dev@budget.test / password');
        $this->command?->info("Guest demo account: {$guest->email} (token via POST /api/v1/auth/guest)");
        $this->command?->info('API token for dev user: '.$user->createToken('seeder-'.Str::random(4))->plainTextToken);
    }
}
