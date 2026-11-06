<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('savings_contributions', function (Blueprint $table) {
            $table->id();
            $table->foreignId('savings_goal_id')->constrained()->cascadeOnDelete();
            $table->foreignId('transaction_id')->nullable()->constrained()->nullOnDelete();
            $table->bigInteger('amount_minor');
            $table->date('contributed_on');
            $table->string('note')->nullable();
            $table->timestamps();

            $table->index(['savings_goal_id', 'contributed_on']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('savings_contributions');
    }
};
