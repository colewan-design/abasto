<?php

namespace App\Http\Requests\V1;

use App\Enums\AccountType;
use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use App\Support\Money;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Rules\Enum;

class CompleteOnboardingRequest extends FormRequest
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
            'currency_code' => ['required', 'string', 'size:3', Rule::in($this->supportedCurrencies())],
            'locale' => ['nullable', 'string', 'max:10'],
            /*
             * all_with_bc, because phones report backward-compatibility zone
             * IDs that the default list leaves out - an emulator set to GMT,
             * or a device reporting Asia/Calcutta or US/Pacific. PHP reads
             * them all; rejecting them strands people on the last step of
             * setup with nothing they can change to get past it.
             */
            'timezone' => ['nullable', 'timezone:all_with_bc'],

            'accounts' => ['required', 'array', 'min:1', 'max:20'],
            'accounts.*.name' => ['required', 'string', 'max:80', 'distinct'],
            'accounts.*.type' => ['required', new Enum(AccountType::class)],
            'accounts.*.opening_balance_minor' => ['sometimes', 'integer', 'between:-999999999999,999999999999'],
            'accounts.*.credit_limit_minor' => ['nullable', 'integer', 'min:0'],
            'accounts.*.institution' => ['nullable', 'string', 'max:80'],
            'accounts.*.color' => ['nullable', 'string', 'max:9'],
            'accounts.*.icon' => ['nullable', 'string', 'max:40'],

            'categories' => ['nullable', 'array', 'max:300'],
            'categories.*' => ['string', 'max:60'],

            'budget' => ['nullable', 'array'],
            'budget.overall_amount_minor' => ['nullable', 'integer', 'min:0', 'max:999999999999'],
            'budget.categories' => ['nullable', 'array', 'max:200'],
            'budget.categories.*.category_name' => ['nullable', 'string', 'max:60'],
            'budget.categories.*.category_id' => ['nullable', 'integer'],
            'budget.categories.*.amount_minor' => ['required_with:budget.categories', 'integer', 'min:0'],
        ];
    }

    /**
     * Convert every major-unit amount in the payload — the wizard posts a
     * nested structure, so this walks it rather than using the flat helper.
     */
    protected function prepareForValidation(): void
    {
        $currency = strtoupper((string) $this->input('currency_code', (string) config('budget.default_currency')));

        $toMinor = function (mixed $value) use ($currency): ?int {
            if ($value === null || $value === '') {
                return null;
            }

            try {
                return Money::ofMajor($value, $currency)->minor;
            } catch (\InvalidArgumentException) {
                return null;
            }
        };

        $accounts = $this->input('accounts');

        if (is_array($accounts)) {
            $this->merge(['accounts' => array_map(function ($account) use ($toMinor): array {
                if (! is_array($account)) {
                    return [];
                }

                foreach (['opening_balance' => 'opening_balance_minor', 'credit_limit' => 'credit_limit_minor'] as $major => $minor) {
                    if (! isset($account[$minor]) && isset($account[$major])) {
                        $account[$minor] = $toMinor($account[$major]);
                    }
                }

                return $account;
            }, $accounts)]);
        }

        $budget = $this->input('budget');

        if (is_array($budget)) {
            if (! isset($budget['overall_amount_minor']) && isset($budget['overall_amount'])) {
                $budget['overall_amount_minor'] = $toMinor($budget['overall_amount']);
            }

            if (is_array($budget['categories'] ?? null)) {
                $budget['categories'] = array_map(function ($line) use ($toMinor): array {
                    if (is_array($line) && ! isset($line['amount_minor']) && isset($line['amount'])) {
                        $line['amount_minor'] = $toMinor($line['amount']);
                    }

                    return is_array($line) ? $line : [];
                }, $budget['categories']);
            }

            $this->merge(['budget' => $budget]);
        }
    }

    public function messages(): array
    {
        return [
            'accounts.required' => 'Add at least one account so balances can be tracked.',
            'accounts.*.name.distinct' => 'Each account needs a different name.',
        ];
    }
}
