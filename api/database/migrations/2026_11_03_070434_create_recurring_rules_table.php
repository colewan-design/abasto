<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('recurring_rules', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('account_id')->constrained()->cascadeOnDelete();
            $table->foreignId('transfer_account_id')->nullable()->constrained('accounts')->nullOnDelete();
            $table->foreignId('category_id')->nullable()->constrained()->nullOnDelete();

            $table->string('name');
            $table->string('type', 20);
            $table->bigInteger('amount_minor');
            $table->char('currency_code', 3);
            $table->string('merchant')->nullable();
            $table->text('note')->nullable();

            $table->string('frequency', 20);
            $table->unsignedTinyInteger('interval')->default(1);
            $table->unsignedTinyInteger('by_month_day')->nullable();
            $table->unsignedTinyInteger('by_weekday')->nullable();

            $table->date('start_date');
            $table->date('end_date')->nullable();
            $table->unsignedSmallInteger('occurrence_limit')->nullable();
            $table->unsignedSmallInteger('occurrences_created')->default(0);
            $table->date('next_run_on')->nullable();
            $table->date('last_run_on')->nullable();

            $table->boolean('auto_post')->default(true);
            $table->boolean('is_active')->default(true);
            $table->timestamps();

            $table->index(['user_id', 'is_active']);
            $table->index(['is_active', 'next_run_on']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('recurring_rules');
    }
};
