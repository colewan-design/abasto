<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('transactions', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('account_id')->constrained()->cascadeOnDelete();
            $table->foreignId('transfer_account_id')->nullable()->constrained('accounts')->nullOnDelete();
            $table->foreignId('category_id')->nullable()->constrained()->nullOnDelete();

            $table->string('type', 20);
            $table->string('direction', 10);
            $table->bigInteger('amount_minor');
            $table->char('currency_code', 3);

            $table->date('occurred_on');
            $table->timestamp('occurred_at')->nullable();

            $table->string('merchant')->nullable();
            $table->string('merchant_normalized')->nullable();
            $table->text('note')->nullable();

            $table->char('transfer_group', 36)->nullable();
            $table->foreignId('receipt_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('bill_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('savings_goal_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('recurring_rule_id')->nullable()->constrained()->nullOnDelete();

            $table->boolean('has_splits')->default(false);
            $table->boolean('is_demo')->default(false);
            $table->string('created_via', 20)->default('manual');
            $table->string('external_ref')->nullable();

            $table->timestamps();
            $table->softDeletes();

            $table->index(['user_id', 'occurred_on']);
            $table->index(['user_id', 'type', 'occurred_on']);
            $table->index(['user_id', 'category_id', 'occurred_on']);
            $table->index(['user_id', 'account_id', 'occurred_on']);
            $table->index(['user_id', 'merchant_normalized']);
            $table->index('transfer_group');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('transactions');
    }
};
