<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->string('google_id')->nullable()->unique()->after('email');
            $table->string('avatar_url')->nullable()->after('google_id');
            $table->char('currency_code', 3)->default(config('budget.default_currency'))->after('avatar_url');
            $table->string('locale', 10)->default('en')->after('currency_code');
            $table->string('timezone', 64)->default('UTC')->after('locale');
            $table->timestamp('onboarding_completed_at')->nullable()->after('timezone');
            $table->boolean('is_demo')->default(false)->after('onboarding_completed_at');
            $table->timestamp('demo_seeded_at')->nullable()->after('is_demo');
            $table->timestamp('last_active_at')->nullable()->after('demo_seeded_at');
            $table->boolean('app_lock_enabled')->default(false)->after('last_active_at');

            $table->index(['is_demo', 'last_active_at']);
        });

        Schema::table('users', function (Blueprint $table) {
            $table->string('password')->nullable()->change();
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropIndex(['is_demo', 'last_active_at']);
            $table->dropColumn([
                'google_id',
                'avatar_url',
                'currency_code',
                'locale',
                'timezone',
                'onboarding_completed_at',
                'is_demo',
                'demo_seeded_at',
                'last_active_at',
                'app_lock_enabled',
            ]);
        });
    }
};
