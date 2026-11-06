<?php

namespace App\Http\Requests\Concerns;

use App\Support\Money;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Rules\Exists;

/**
 * Rules that keep a request from referencing another user's records, plus the
 * conversion from the major-unit amounts clients send to the integer minor
 * units the ledger stores.
 */
trait ValidatesOwnedRecords
{
    protected function ownedBy(string $table): Exists
    {
        return Rule::exists($table, 'id')->where('user_id', $this->user()?->getKey());
    }

    protected function ownedAccount(): Exists
    {
        return $this->ownedBy('accounts');
    }

    protected function ownedCategory(?string $type = null): Exists
    {
        $rule = $this->ownedBy('categories')->whereNull('archived_at');

        return $type === null ? $rule : $rule->where('type', $type);
    }

    protected function ownedTag(): Exists
    {
        return $this->ownedBy('tags');
    }

    protected function ownedSavingsGoal(): Exists
    {
        return $this->ownedBy('savings_goals');
    }

    protected function ownedBill(): Exists
    {
        return $this->ownedBy('bills');
    }

    /**
     * Accept either `amount` in major units (`"1,234.56"`) or `amount_minor`
     * as an integer, and normalise to the minor field.
     *
     * @param  array<string, string>  $fields  major field => minor field
     */
    protected function normalizeMoney(array $fields): void
    {
        $currency = $this->currencyForConversion();
        $merge = [];

        foreach ($fields as $major => $minor) {
            if ($this->filled($minor) || ! $this->filled($major)) {
                continue;
            }

            $value = $this->input($major);

            if (! is_numeric($value) && ! is_string($value)) {
                continue;
            }

            try {
                $merge[$minor] = Money::ofMajor($value, $currency)->minor;
            } catch (\InvalidArgumentException) {
                // Leave the field unset so validation reports it rather than
                // failing with an exception from the conversion itself.
            }
        }

        if ($merge !== []) {
            $this->merge($merge);
        }
    }

    protected function currencyForConversion(): string
    {
        return $this->string('currency_code')->isNotEmpty()
            ? strtoupper($this->string('currency_code')->toString())
            : (string) ($this->user()?->currency_code ?? config('budget.default_currency'));
    }

    /**
     * @return array<int, string>
     */
    protected function supportedCurrencies(): array
    {
        return array_keys((array) config('budget.currencies'));
    }
}
