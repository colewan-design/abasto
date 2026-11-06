<?php

namespace App\Http\Requests\V1;

use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class PayBillRequest extends FormRequest
{
    use ValidatesOwnedRecords;

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
            'account_id' => ['nullable', 'integer', $this->ownedAccount()],
            'amount_minor' => ['nullable', 'integer', 'min:1', 'max:999999999999'],
            'paid_on' => ['nullable', 'date_format:Y-m-d'],
            'note' => ['nullable', 'string', 'max:2000'],
            /*
             * Clear this when the payment was already entered as an expense,
             * so marking the bill paid does not record it a second time.
             */
            'record_expense' => ['nullable', 'boolean'],
        ];
    }

    protected function prepareForValidation(): void
    {
        $this->normalizeMoney(['amount' => 'amount_minor']);
    }
}
