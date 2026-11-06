<?php

namespace App\Http\Requests\V1;

use App\Enums\TransactionType;
use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use App\Support\Money;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class StoreTransactionRequest extends FormRequest
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
            'type' => ['required', Rule::in([TransactionType::Income->value, TransactionType::Expense->value])],
            'account_id' => ['required', 'integer', $this->ownedAccount()],
            'category_id' => ['nullable', 'integer', $this->ownedCategory($this->input('type'))],
            'amount_minor' => ['required', 'integer', 'min:1', 'max:999999999999'],
            'occurred_on' => ['required', 'date_format:Y-m-d', 'before_or_equal:'.today()->addYear()->toDateString()],
            'occurred_at' => ['nullable', 'date'],
            'merchant' => ['nullable', 'string', 'max:160'],
            'note' => ['nullable', 'string', 'max:2000'],
            'receipt_id' => ['nullable', 'integer', $this->ownedBy('receipts')],
            'bill_id' => ['nullable', 'integer', $this->ownedBill()],
            'savings_goal_id' => ['nullable', 'integer', $this->ownedSavingsGoal()],
            'tags' => ['nullable', 'array', 'max:20'],
            'tags.*' => ['integer', $this->ownedTag()],
            'splits' => ['nullable', 'array', 'max:40'],
            'splits.*.category_id' => ['nullable', 'integer', $this->ownedCategory($this->input('type'))],
            'splits.*.amount_minor' => ['required_with:splits', 'integer', 'min:1'],
            'splits.*.note' => ['nullable', 'string', 'max:200'],
        ];
    }

    protected function prepareForValidation(): void
    {
        $this->normalizeMoney(['amount' => 'amount_minor']);

        $splits = $this->input('splits');

        if (! is_array($splits)) {
            return;
        }

        $currency = $this->currencyForConversion();

        $this->merge([
            'splits' => array_map(function ($split) use ($currency): array {
                if (is_array($split) && ! isset($split['amount_minor']) && isset($split['amount'])) {
                    $split['amount_minor'] = Money::ofMajor($split['amount'], $currency)->minor;
                }

                return is_array($split) ? $split : [];
            }, $splits),
        ]);
    }

    public function messages(): array
    {
        return [
            'amount_minor.required' => 'Enter an amount.',
            'amount_minor.min' => 'The amount must be greater than zero.',
            'occurred_on.required' => 'Choose the date of the transaction.',
        ];
    }
}
