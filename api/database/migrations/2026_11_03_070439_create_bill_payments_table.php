<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('bill_payments', function (Blueprint $table) {
            $table->id();
            $table->foreignId('bill_id')->constrained()->cascadeOnDelete();
            $table->foreignId('transaction_id')->nullable()->constrained()->nullOnDelete();
            $table->date('period_due_date');
            $table->bigInteger('amount_minor');
            $table->date('paid_on')->nullable();
            $table->string('status', 20)->default('unpaid');
            $table->timestamps();

            $table->unique(['bill_id', 'period_due_date']);
            $table->index(['bill_id', 'status']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('bill_payments');
    }
};
