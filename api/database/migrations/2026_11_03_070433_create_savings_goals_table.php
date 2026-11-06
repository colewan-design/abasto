<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('savings_goals', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('account_id')->nullable()->constrained()->nullOnDelete();
            $table->string('name');
            $table->char('currency_code', 3);
            $table->bigInteger('target_amount_minor');
            $table->bigInteger('current_amount_minor')->default(0);
            $table->date('target_date')->nullable();
            $table->string('color', 9)->nullable();
            $table->string('icon', 40)->nullable();
            $table->text('note')->nullable();
            $table->timestamp('achieved_at')->nullable();
            $table->boolean('is_archived')->default(false);
            $table->unsignedSmallInteger('sort_order')->default(0);
            $table->timestamps();

            $table->unique(['user_id', 'name']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('savings_goals');
    }
};
