<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Merchant -> category mappings learned from a user's own corrections.
     *
     * The knowledge that ships with the app lives in `categories.keywords`
     * instead, because a shared rule has no category id to point at until a
     * user's categories exist.
     */
    public function up(): void
    {
        Schema::create('merchant_category_rules', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('category_id')->constrained()->cascadeOnDelete();
            $table->string('merchant_pattern');
            $table->string('match_type', 20)->default('contains');
            $table->string('source', 20)->default('user');
            $table->unsignedInteger('hit_count')->default(1);
            $table->timestamp('last_used_at')->nullable();
            $table->timestamps();

            $table->unique(['user_id', 'merchant_pattern', 'category_id'], 'merchant_rules_unique');
            $table->index('merchant_pattern');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('merchant_category_rules');
    }
};
