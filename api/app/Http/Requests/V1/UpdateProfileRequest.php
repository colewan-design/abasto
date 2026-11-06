<?php

namespace App\Http\Requests\V1;

use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class UpdateProfileRequest extends FormRequest
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
            'name' => ['sometimes', 'string', 'max:120'],
            'currency_code' => ['sometimes', 'string', 'size:3', Rule::in($this->supportedCurrencies())],
            'locale' => ['sometimes', 'string', 'max:10'],
            'timezone' => ['sometimes', 'timezone:all_with_bc'],
            'app_lock_enabled' => ['sometimes', 'boolean'],
            'password' => ['sometimes', 'nullable', 'string', 'min:8', 'max:200', 'confirmed'],
            /*
             * A Google-only account has no password yet, so the first one it
             * sets cannot be asked to confirm a previous password.
             */
            'current_password' => $this->user()?->password === null
                ? ['nullable']
                : ['required_with:password', 'current_password:sanctum'],
        ];
    }

    public function messages(): array
    {
        return [
            'current_password.current_password' => 'That password does not match your current one.',
        ];
    }
}
