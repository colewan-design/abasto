<?php

namespace App\Http\Requests\V1;

use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use App\Support\Money;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class UpdateTransactionRequest extends FormRequest
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
        $type = $this->route('transaction')?->type->value;

        return [
            'account_id' => ['sometimes', 'integer', $this->ownedAccount()],
            'category_id' => ['nullable', 'integer', $this->ownedCategory($type)],
            'amount_minor' => ['sometimes', 'integer', 'min:1', 'max:999999999999'],
            'occurred_on' => ['sometimes', 'date_format:Y-m-d'],
            'occurred_at' => ['nullable', 'date'],
            'merchant' => ['nullable', 'string', 'max:160'],
            'note' => ['nullable', 'string', 'max:2000'],
            'savings_goal_id' => ['nullable', 'integer', $this->ownedSavingsGoal()],
            'tags' => ['sometimes', 'array', 'max:20'],
            'tags.*' => ['integer', $this->ownedTag()],
            'splits' => ['sometimes', 'nullable', 'array', 'max:40'],
            'splits.*.category_id' => ['nullable', 'integer', $this->ownedCategory($type)],
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

        $currency = $this->route('transaction')?->currency_code ?? $this->currencyForConversion();

        $this->merge([
            'splits' => array_map(function ($split) use ($currency): array {
                if (is_array($split) && ! isset($split['amount_minor']) && isset($split['amount'])) {
                    $split['amount_minor'] = Money::ofMajor($split['amount'], $currency)->minor;
                }

                return is_array($split) ? $split : [];
            }, $splits),
        ]);
    }
}
