<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('accounts', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('name');
            $table->string('type', 20);
            $table->char('currency_code', 3);
            $table->bigInteger('opening_balance_minor')->default(0);
            $table->bigInteger('current_balance_minor')->default(0);
            $table->bigInteger('credit_limit_minor')->nullable();
            $table->string('institution')->nullable();
            $table->char('number_last4', 4)->nullable();
            $table->string('color', 9)->nullable();
            $table->string('icon', 40)->nullable();
            $table->boolean('include_in_total')->default(true);
            $table->boolean('is_archived')->default(false);
            $table->unsignedSmallInteger('sort_order')->default(0);
            $table->timestamps();

            $table->unique(['user_id', 'name']);
            $table->index(['user_id', 'is_archived']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('accounts');
    }
};
