<?php

namespace App\Http\Requests\V1;

use App\Enums\CategoryType;
use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use App\Support\Money;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class UpdateBudgetRequest extends FormRequest
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
            'month' => ['nullable', 'date_format:Y-m'],
            'overall_amount_minor' => ['nullable', 'integer', 'min:0', 'max:999999999999'],
            'rollover_enabled' => ['sometimes', 'boolean'],
            'alert_threshold' => ['sometimes', 'numeric', 'min:0.1', 'max:1'],
            'categories' => ['sometimes', 'array', 'max:200'],
            'categories.*.category_id' => ['required_with:categories', 'integer', $this->ownedCategory(CategoryType::Expense->value)],
            'categories.*.amount_minor' => ['required_with:categories', 'integer', 'min:0', 'max:999999999999'],
            'categories.*.alert_threshold' => ['nullable', 'numeric', 'min:0.1', 'max:1'],
        ];
    }

    protected function prepareForValidation(): void
    {
        $this->normalizeMoney(['overall_amount' => 'overall_amount_minor']);

        $categories = $this->input('categories');

        if (! is_array($categories)) {
            return;
        }

        $currency = $this->currencyForConversion();

        $this->merge([
            'categories' => array_map(function ($line) use ($currency): array {
                if (is_array($line) && ! isset($line['amount_minor']) && isset($line['amount'])) {
                    $line['amount_minor'] = Money::ofMajor($line['amount'], $currency)->minor;
                }

                return is_array($line) ? $line : [];
            }, $categories),
        ]);
    }
}
