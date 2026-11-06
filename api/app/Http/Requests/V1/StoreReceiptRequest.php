<?php

namespace App\Http\Requests\V1;

use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class StoreReceiptRequest extends FormRequest
{
    public function authorize(): bool
    {
        return $this->user() !== null;
    }

    /**
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'image' => [
                'required', 'file', 'image',
                'mimes:jpeg,jpg,png,webp,heic,heif',
                'max:'.config('budget.receipts.max_upload_kb'),
            ],
            /*
             * Text the client recognised on-device. Sending it lets the server
             * skip a paid OCR call, and keeps capture working offline.
             */
            'recognized_text' => ['nullable', 'string', 'max:20000'],
            'captured_at' => ['nullable', 'date'],
            'process' => ['nullable', 'boolean'],
        ];
    }

    public function messages(): array
    {
        return [
            'image.required' => 'Attach a photo of the receipt.',
            'image.max' => 'That image is too large. Try a smaller photo.',
        ];
    }
}
