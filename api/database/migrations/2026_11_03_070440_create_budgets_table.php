<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('budgets', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('period', 20)->default('monthly');
            $table->date('period_start');
            $table->date('period_end');
            $table->char('currency_code', 3);
            $table->bigInteger('overall_amount_minor')->nullable();
            $table->boolean('rollover_enabled')->default(false);
            $table->decimal('alert_threshold', 3, 2)->default(0.80);
            $table->timestamps();

            $table->unique(['user_id', 'period_start']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('budgets');
    }
};
