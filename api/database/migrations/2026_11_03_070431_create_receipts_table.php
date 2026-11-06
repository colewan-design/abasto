<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('receipts', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('image_path');
            $table->string('thumbnail_path')->nullable();
            $table->string('original_filename')->nullable();
            $table->string('mime_type', 60)->nullable();
            $table->unsignedBigInteger('size_bytes')->nullable();

            $table->string('status', 20)->default('pending');
            $table->string('ocr_driver', 30)->nullable();
            $table->decimal('ocr_confidence', 4, 3)->nullable();
            $table->string('failure_reason')->nullable();

            $table->string('merchant')->nullable();
            $table->string('merchant_normalized')->nullable();
            $table->date('receipt_date')->nullable();
            $table->char('currency_code', 3)->nullable();
            $table->bigInteger('subtotal_minor')->nullable();
            $table->bigInteger('tax_minor')->nullable();
            $table->bigInteger('tip_minor')->nullable();
            $table->bigInteger('discount_minor')->nullable();
            $table->bigInteger('total_minor')->nullable();
            $table->string('payment_method', 40)->nullable();

            $table->longText('raw_text')->nullable();
            $table->json('extracted')->nullable();
            $table->json('field_confidence')->nullable();

            $table->char('content_hash', 64)->nullable();
            $table->foreignId('duplicate_of_receipt_id')->nullable()->constrained('receipts')->nullOnDelete();
            $table->decimal('duplicate_score', 4, 3)->nullable();

            $table->boolean('is_demo')->default(false);
            $table->timestamp('processed_at')->nullable();
            $table->timestamp('confirmed_at')->nullable();
            $table->timestamps();
            $table->softDeletes();

            $table->index(['user_id', 'status']);
            $table->index(['user_id', 'receipt_date']);
            $table->index(['user_id', 'content_hash']);
            $table->index(['user_id', 'merchant_normalized']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('receipts');
    }
};
