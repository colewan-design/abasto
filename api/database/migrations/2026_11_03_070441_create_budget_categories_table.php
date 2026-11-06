<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('budget_categories', function (Blueprint $table) {
            $table->id();
            $table->foreignId('budget_id')->constrained()->cascadeOnDelete();
            $table->foreignId('category_id')->constrained()->cascadeOnDelete();
            $table->bigInteger('amount_minor');
            $table->bigInteger('rollover_minor')->default(0);
            $table->decimal('alert_threshold', 3, 2)->nullable();
            $table->timestamp('approaching_alerted_at')->nullable();
            $table->timestamp('exceeded_alerted_at')->nullable();
            $table->timestamps();

            $table->unique(['budget_id', 'category_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('budget_categories');
    }
};
