<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('receipt_items', function (Blueprint $table) {
            $table->id();
            $table->foreignId('receipt_id')->constrained()->cascadeOnDelete();
            $table->unsignedSmallInteger('line_no')->default(0);
            $table->string('description');
            $table->decimal('quantity', 10, 3)->default(1);
            $table->bigInteger('unit_price_minor')->nullable();
            $table->bigInteger('total_minor')->default(0);
            $table->foreignId('suggested_category_id')->nullable()->constrained('categories')->nullOnDelete();
            $table->foreignId('category_id')->nullable()->constrained()->nullOnDelete();
            $table->decimal('confidence', 4, 3)->nullable();
            $table->timestamps();

            $table->index(['receipt_id', 'line_no']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('receipt_items');
    }
};
