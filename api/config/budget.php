<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Currency
    |--------------------------------------------------------------------------
    |
    | Amounts are stored as integer minor units. `currencies` lists the options
    | offered during guided setup together with the exponent used to convert
    | between major and minor units.
    |
    */

    'default_currency' => env('BUDGET_DEFAULT_CURRENCY', 'PHP'),

    'currencies' => [
        'PHP' => ['name' => 'Philippine Peso', 'symbol' => '₱', 'exponent' => 2],
        'USD' => ['name' => 'US Dollar', 'symbol' => '$', 'exponent' => 2],
        'EUR' => ['name' => 'Euro', 'symbol' => '€', 'exponent' => 2],
        'GBP' => ['name' => 'British Pound', 'symbol' => '£', 'exponent' => 2],
        'JPY' => ['name' => 'Japanese Yen', 'symbol' => '¥', 'exponent' => 0],
        'SGD' => ['name' => 'Singapore Dollar', 'symbol' => 'S$', 'exponent' => 2],
        'AUD' => ['name' => 'Australian Dollar', 'symbol' => 'A$', 'exponent' => 2],
        'CAD' => ['name' => 'Canadian Dollar', 'symbol' => 'C$', 'exponent' => 2],
        'HKD' => ['name' => 'Hong Kong Dollar', 'symbol' => 'HK$', 'exponent' => 2],
        'AED' => ['name' => 'UAE Dirham', 'symbol' => 'د.إ', 'exponent' => 2],
        'INR' => ['name' => 'Indian Rupee', 'symbol' => '₹', 'exponent' => 2],
        'IDR' => ['name' => 'Indonesian Rupiah', 'symbol' => 'Rp', 'exponent' => 0],
        'MYR' => ['name' => 'Malaysian Ringgit', 'symbol' => 'RM', 'exponent' => 2],
        'THB' => ['name' => 'Thai Baht', 'symbol' => '฿', 'exponent' => 2],
        'VND' => ['name' => 'Vietnamese Dong', 'symbol' => '₫', 'exponent' => 0],
        'KRW' => ['name' => 'South Korean Won', 'symbol' => '₩', 'exponent' => 0],
    ],

    /*
    |--------------------------------------------------------------------------
    | Budgets
    |--------------------------------------------------------------------------
    |
    | `alert_threshold` is the default share of a category limit that triggers
    | an "approaching your limit" notification.
    |
    */

    'alert_threshold' => (float) env('BUDGET_ALERT_THRESHOLD', 0.8),

    /*
    |--------------------------------------------------------------------------
    | Guest demo
    |--------------------------------------------------------------------------
    */

    'demo' => [
        'retention_days' => (int) env('BUDGET_DEMO_RETENTION_DAYS', 7),
        'currency' => env('BUDGET_DEMO_CURRENCY', 'PHP'),
        'months_of_history' => 3,
    ],

    /*
    |--------------------------------------------------------------------------
    | Receipts
    |--------------------------------------------------------------------------
    |
    | `duplicate_window_days` and `duplicate_amount_tolerance` drive the fuzzy
    | duplicate warning shown on the receipt review screen.
    |
    */

    'receipts' => [
        'disk' => 'receipts',
        'max_upload_kb' => (int) env('RECEIPT_MAX_UPLOAD_KB', 12288),
        'thumbnail_width' => 480,
        'duplicate_window_days' => 5,
        'duplicate_amount_tolerance' => 0.01,
        'low_confidence_threshold' => 0.7,
    ],

    'ocr' => [
        'driver' => env('RECEIPT_OCR_DRIVER', 'heuristic'),

        'anthropic' => [
            'api_key' => env('ANTHROPIC_API_KEY'),
            'model' => env('ANTHROPIC_VISION_MODEL', 'claude-opus-5'),
            'base_url' => env('ANTHROPIC_BASE_URL', 'https://api.anthropic.com'),
            'version' => '2023-06-01',
            'max_tokens' => 4096,
            'timeout' => 60,
        ],

        'google_vision' => [
            'api_key' => env('GOOGLE_VISION_API_KEY'),
            'endpoint' => 'https://vision.googleapis.com/v1/images:annotate',
            'timeout' => 30,
        ],

        'tesseract' => [
            'binary' => env('TESSERACT_BINARY', 'tesseract'),
            'language' => env('TESSERACT_LANGUAGE', 'eng'),
            'timeout' => 60,
        ],
    ],

    /*
    |--------------------------------------------------------------------------
    | Exports
    |--------------------------------------------------------------------------
    */

    'exports' => [
        'max_rows' => 50000,
        'paper' => 'a4',
    ],
];
