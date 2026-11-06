<?php

namespace App\Http\Requests\V1;

use App\Enums\CategoryType;
use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use App\Support\Money;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Validator;

class ConfirmReceiptRequest extends FormRequest
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
            'account_id' => ['required', 'integer', $this->ownedAccount()],
            'category_id' => ['nullable', 'integer', $this->ownedCategory(CategoryType::Expense->value)],
            'merchant' => ['nullable', 'string', 'max:160'],
            'receipt_date' => ['nullable', 'date_format:Y-m-d'],
            'total_minor' => ['nullable', 'integer', 'min:1', 'max:999999999999'],
            'currency_code' => ['nullable', 'string', 'size:3', Rule::in($this->supportedCurrencies())],
            'payment_method' => ['nullable', 'string', 'max:40'],
            'note' => ['nullable', 'string', 'max:2000'],
            'acknowledge_duplicate' => ['nullable', 'boolean'],
            'tags' => ['nullable', 'array', 'max:20'],
            'tags.*' => ['integer', $this->ownedTag()],

            'items' => ['nullable', 'array', 'max:200'],
            'items.*.id' => ['required_with:items', 'integer'],
            'items.*.category_id' => ['nullable', 'integer', $this->ownedCategory(CategoryType::Expense->value)],

            'splits' => ['nullable', 'array', 'max:40'],
            'splits.*.category_id' => ['nullable', 'integer', $this->ownedCategory(CategoryType::Expense->value)],
            'splits.*.amount_minor' => ['required_with:splits', 'integer', 'min:1'],
            'splits.*.note' => ['nullable', 'string', 'max:200'],
        ];
    }

    protected function prepareForValidation(): void
    {
        $this->normalizeMoney(['total' => 'total_minor']);

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

    /**
     * Keep the receipt's own line items pointing at categories the user owns.
     */
    public function withValidator(Validator $validator): void
    {
        $validator->after(function (Validator $validator): void {
            $receipt = $this->route('receipt');
            $items = $this->input('items');

            if ($receipt === null || ! is_array($items)) {
                return;
            }

            $owned = $receipt->items()->pluck('id')->all();

            foreach ($items as $index => $item) {
                if (! in_array((int) ($item['id'] ?? 0), $owned, true)) {
                    $validator->errors()->add("items.{$index}.id", 'That line is not part of this receipt.');
                }
            }
        });
    }
}
