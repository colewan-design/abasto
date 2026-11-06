<?php

namespace App\Http\Requests\V1;

use App\Enums\AccountType;
use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Rules\Enum;

class StoreAccountRequest extends FormRequest
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
            'name' => [
                'required', 'string', 'max:80',
                Rule::unique('accounts', 'name')->where('user_id', $this->user()->getKey()),
            ],
            'type' => ['required', new Enum(AccountType::class)],
            'currency_code' => ['sometimes', 'string', 'size:3', Rule::in($this->supportedCurrencies())],
            'opening_balance_minor' => ['sometimes', 'integer', 'between:-999999999999,999999999999'],
            'credit_limit_minor' => ['nullable', 'integer', 'min:0', 'max:999999999999'],
            'institution' => ['nullable', 'string', 'max:80'],
            'number_last4' => ['nullable', 'string', 'size:4'],
            'color' => ['nullable', 'string', 'max:9'],
            'icon' => ['nullable', 'string', 'max:40'],
            'include_in_total' => ['sometimes', 'boolean'],
            'sort_order' => ['sometimes', 'integer', 'min:0', 'max:999'],
        ];
    }

    protected function prepareForValidation(): void
    {
        $this->normalizeMoney([
            'opening_balance' => 'opening_balance_minor',
            'credit_limit' => 'credit_limit_minor',
        ]);
    }

    public function messages(): array
    {
        return [
            'name.unique' => 'You already have an account with that name.',
        ];
    }
}
