<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('bills', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('account_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('category_id')->nullable()->constrained()->nullOnDelete();

            $table->string('name');
            $table->string('payee')->nullable();
            $table->bigInteger('amount_minor');
            $table->char('currency_code', 3);
            $table->date('due_date');
            $table->string('recurrence', 20)->default('monthly');
            $table->unsignedTinyInteger('recurrence_interval')->default(1);
            $table->unsignedTinyInteger('reminder_days_before')->default(3);
            $table->boolean('auto_pay')->default(false);
            $table->boolean('is_active')->default(true);
            $table->text('note')->nullable();
            $table->date('last_paid_on')->nullable();
            $table->timestamp('reminded_at')->nullable();
            $table->timestamps();

            $table->index(['user_id', 'is_active', 'due_date']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('bills');
    }
};
