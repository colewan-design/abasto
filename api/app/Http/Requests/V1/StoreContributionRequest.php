<?php

namespace App\Http\Requests\V1;

use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class StoreContributionRequest extends FormRequest
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
            'amount_minor' => ['required', 'integer', 'min:1', 'max:999999999999'],
            'contributed_on' => ['required', 'date_format:Y-m-d'],
            /*
             * Optional: when given, the contribution also moves money out of
             * this account so balances stay truthful. Without it the goal is
             * updated on its own, for money set aside outside the app.
             */
            'from_account_id' => ['nullable', 'integer', $this->ownedAccount()],
            'note' => ['nullable', 'string', 'max:500'],
        ];
    }

    protected function prepareForValidation(): void
    {
        $this->normalizeMoney(['amount' => 'amount_minor']);
    }
}
