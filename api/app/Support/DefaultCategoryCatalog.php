<?php

namespace App\Support;

/**
 * The starter set of groups and categories offered during guided setup.
 *
 * `keywords` seed both merchant matching and item-level suggestions, so a
 * receipt from "Shell Select" lands in Fuel before the user has corrected
 * anything.
 */
final class DefaultCategoryCatalog
{
    /**
     * @return array<string, array<int, array{
     *     name: string,
     *     icon: string,
     *     color: string,
     *     categories: array<int, array{name: string, icon: string, keywords: array<int, string>}>
     * }>>
     */
    public static function all(): array
    {
        return [
            'expense' => self::expenseGroups(),
            'income' => self::incomeGroups(),
        ];
    }

    /**
     * Categories the guided setup pre-selects for a new account.
     *
     * @return array<int, string>
     */
    public static function recommended(): array
    {
        return [
            'Groceries',
            'Utilities',
            'Rent',
            'Restaurants',
            'Transportation',
            'Internet & Phone',
            'Health',
            'Shopping',
            'Entertainment',
            'Salary',
            'Other Income',
        ];
    }

    /**
     * @return array<int, array{name: string, icon: string, color: string, categories: array<int, array{name: string, icon: string, keywords: array<int, string>}>}>
     */
    private static function expenseGroups(): array
    {
        return [
            [
                'name' => 'Living Expenses',
                'icon' => 'home',
                'color' => '#2563eb',
                'categories' => [
                    ['name' => 'Groceries', 'icon' => 'shopping-cart', 'keywords' => [
                        'supermarket', 'grocery', 'market', 'mart', 'puregold', 'robinsons supermarket',
                        'sm supermarket', 'waltermart', 'landers', 's&r', 'rustans', 'metro market',
                        'walmart', 'kroger', 'aldi', 'tesco', 'costco', 'safeway', 'trader joe',
                        'rice', 'bread', 'milk', 'eggs', 'chicken', 'pork', 'beef', 'fish',
                        'vegetables', 'fruit', 'produce', 'cooking oil', 'sugar', 'flour', 'coffee beans',
                    ]],
                    ['name' => 'Household Supplies', 'icon' => 'spray-bottle', 'keywords' => [
                        'detergent', 'dishwashing', 'bleach', 'tissue', 'paper towel', 'trash bag',
                        'soap', 'cleaner', 'disinfectant', 'fabric conditioner', 'sponge', 'broom',
                        'ace hardware', 'wilcon', 'home depot', 'true value', 'hardware',
                    ]],
                    ['name' => 'Rent', 'icon' => 'key', 'keywords' => [
                        'rent', 'lease', 'landlord', 'apartment', 'condo dues', 'association dues',
                    ]],
                    ['name' => 'Utilities', 'icon' => 'bolt', 'keywords' => [
                        'electric', 'electricity', 'meralco', 'water', 'maynilad', 'manila water',
                        'gas bill', 'lpg', 'petron gasul', 'utility', 'power', 'sewer', 'con ed',
                    ]],
                    ['name' => 'Internet & Phone', 'icon' => 'wifi', 'keywords' => [
                        'internet', 'broadband', 'fiber', 'pldt', 'globe', 'smart', 'converge',
                        'sky cable', 'dito', 'load', 'prepaid', 'postpaid', 'mobile plan', 'at&t',
                        'verizon', 't-mobile', 'vodafone',
                    ]],
                ],
            ],
            [
                'name' => 'Food & Drink',
                'icon' => 'utensils',
                'color' => '#f97316',
                'categories' => [
                    ['name' => 'Restaurants', 'icon' => 'utensils', 'keywords' => [
                        'restaurant', 'grill', 'kitchen', 'bistro', 'diner', 'cafeteria', 'jollibee',
                        'mcdonald', 'kfc', 'chowking', 'mang inasal', 'greenwich', 'pizza hut',
                        'shakey', 'max', 'yellow cab', 'bonchon', 'subway', 'burger king', 'wendy',
                        'taco bell', 'chipotle', 'panda express', 'ramen', 'sushi', 'buffet',
                    ]],
                    ['name' => 'Coffee & Snacks', 'icon' => 'coffee', 'keywords' => [
                        'coffee', 'cafe', 'starbucks', 'coffee bean', 'tim hortons', 'dunkin',
                        'bo s coffee', 'tim', 'milk tea', 'chatime', 'coco', 'serenitea', 'macao',
                        'bakery', 'pastry', 'donut', 'ice cream', 'dairy queen', 'bread talk',
                    ]],
                    ['name' => 'Food Delivery', 'icon' => 'moped', 'keywords' => [
                        'grabfood', 'foodpanda', 'doordash', 'ubereats', 'uber eats', 'deliveroo',
                        'lalafood', 'delivery fee', 'just eat', 'grubhub',
                    ]],
                ],
            ],
            [
                'name' => 'Transportation',
                'icon' => 'car',
                'color' => '#0ea5e9',
                'categories' => [
                    ['name' => 'Fuel', 'icon' => 'fuel', 'keywords' => [
                        'shell', 'petron', 'caltex', 'phoenix petroleum', 'seaoil', 'total',
                        'unioil', 'gasoline', 'diesel', 'fuel', 'exxon', 'chevron', 'bp', 'esso',
                    ]],
                    ['name' => 'Ride-hailing', 'icon' => 'car', 'keywords' => [
                        'grab', 'uber', 'lyft', 'angkas', 'joyride', 'move it', 'taxi', 'bolt',
                    ]],
                    ['name' => 'Public Transport', 'icon' => 'train', 'keywords' => [
                        'mrt', 'lrt', 'beep', 'jeepney', 'bus fare', 'p2p', 'ferry', 'train',
                        'metro card', 'transit', 'subway fare',
                    ]],
                    ['name' => 'Parking & Tolls', 'icon' => 'parking', 'keywords' => [
                        'parking', 'toll', 'easytrip', 'autosweep', 'skyway', 'nlex', 'slex', 'e-pass',
                    ]],
                    ['name' => 'Vehicle Maintenance', 'icon' => 'wrench', 'keywords' => [
                        'car wash', 'change oil', 'auto parts', 'tire', 'casa', 'registration lto',
                        'mechanic', 'auto service', 'jiffy lube',
                    ]],
                ],
            ],
            [
                'name' => 'Health & Wellness',
                'icon' => 'heart-pulse',
                'color' => '#ef4444',
                'categories' => [
                    ['name' => 'Health', 'icon' => 'stethoscope', 'keywords' => [
                        'hospital', 'clinic', 'doctor', 'dentist', 'dental', 'laboratory', 'medical',
                        'checkup', 'consultation', 'optical', 'eye center', 'therapy',
                    ]],
                    ['name' => 'Pharmacy', 'icon' => 'pill', 'keywords' => [
                        'pharmacy', 'mercury drug', 'watsons', 'rose pharmacy', 'generika',
                        'southstar drug', 'walgreens', 'cvs', 'boots', 'medicine', 'paracetamol',
                        'vitamins', 'antibiotic', 'prescription',
                    ]],
                    ['name' => 'Fitness', 'icon' => 'dumbbell', 'keywords' => [
                        'gym', 'fitness', 'anytime fitness', 'gold s gym', 'yoga', 'pilates',
                        'crossfit', 'sports club', 'planet fitness',
                    ]],
                    ['name' => 'Insurance', 'icon' => 'shield', 'keywords' => [
                        'insurance', 'philhealth', 'pacific cross', 'maxicare', 'intellicare',
                        'sun life', 'manulife', 'pru life', 'axa', 'premium payment',
                    ]],
                ],
            ],
            [
                'name' => 'Shopping',
                'icon' => 'shopping-bag',
                'color' => '#a855f7',
                'categories' => [
                    ['name' => 'Shopping', 'icon' => 'shopping-bag', 'keywords' => [
                        'shopee', 'lazada', 'zalora', 'amazon', 'ebay', 'temu', 'tiktok shop',
                        'department store', 'sm store', 'robinsons', 'landmark', 'target',
                    ]],
                    ['name' => 'Clothing', 'icon' => 'shirt', 'keywords' => [
                        'uniqlo', 'h&m', 'zara', 'bench', 'penshoppe', 'cotton on', 'nike',
                        'adidas', 'skechers', 'shoes', 'apparel', 'clothing', 'forever 21',
                    ]],
                    ['name' => 'Electronics', 'icon' => 'laptop', 'keywords' => [
                        'apple', 'samsung', 'lenovo', 'asus', 'acer', 'power mac', 'octagon',
                        'datablitz', 'pc express', 'best buy', 'laptop', 'headphones', 'charger',
                    ]],
                    ['name' => 'Home & Furniture', 'icon' => 'sofa', 'keywords' => [
                        'ikea', 'mandaue foam', 'our home', 'furniture', 'mattress', 'curtain',
                        'home decor', 'wayfair',
                    ]],
                    ['name' => 'Gifts', 'icon' => 'gift', 'keywords' => [
                        'gift', 'present', 'flowers', 'souvenir', 'greeting card',
                    ]],
                ],
            ],
            [
                'name' => 'Entertainment',
                'icon' => 'party-popper',
                'color' => '#ec4899',
                'categories' => [
                    ['name' => 'Entertainment', 'icon' => 'ticket', 'keywords' => [
                        'cinema', 'movie', 'sm cinema', 'ayala cinema', 'concert', 'theater',
                        'amusement', 'arcade', 'ticketnet', 'smtickets', 'bowling', 'billiards',
                    ]],
                    ['name' => 'Subscriptions', 'icon' => 'repeat', 'keywords' => [
                        'netflix', 'spotify', 'youtube premium', 'disney', 'hbo', 'viu', 'icloud',
                        'google one', 'microsoft 365', 'adobe', 'canva', 'notion', 'dropbox',
                        'prime video', 'apple music', 'subscription',
                    ]],
                    ['name' => 'Games & Hobbies', 'icon' => 'gamepad', 'keywords' => [
                        'steam', 'playstation', 'nintendo', 'xbox', 'mobile legends', 'genshin',
                        'roblox', 'game', 'hobby', 'craft',
                    ]],
                    ['name' => 'Travel', 'icon' => 'plane', 'keywords' => [
                        'airline', 'cebu pacific', 'philippine airlines', 'airasia', 'agoda',
                        'booking com', 'airbnb', 'hotel', 'resort', 'travel', 'tour', 'expedia',
                    ]],
                ],
            ],
            [
                'name' => 'Education',
                'icon' => 'graduation-cap',
                'color' => '#14b8a6',
                'categories' => [
                    ['name' => 'Tuition & Fees', 'icon' => 'school', 'keywords' => [
                        'tuition', 'school fee', 'enrollment', 'university', 'college', 'academy',
                        'matriculation',
                    ]],
                    ['name' => 'Books & Supplies', 'icon' => 'book', 'keywords' => [
                        'book', 'bookstore', 'national book store', 'fully booked', 'notebook',
                        'school supplies', 'stationery', 'pen', 'printer ink',
                    ]],
                    ['name' => 'Courses', 'icon' => 'certificate', 'keywords' => [
                        'udemy', 'coursera', 'skillshare', 'masterclass', 'workshop', 'seminar',
                        'training', 'review center',
                    ]],
                ],
            ],
            [
                'name' => 'Financial',
                'icon' => 'landmark',
                'color' => '#64748b',
                'categories' => [
                    ['name' => 'Bank Fees', 'icon' => 'receipt', 'keywords' => [
                        'bank fee', 'service charge', 'atm fee', 'withdrawal fee', 'transfer fee',
                        'instapay', 'pesonet', 'convenience fee', 'maintaining balance',
                    ]],
                    ['name' => 'Loan Payment', 'icon' => 'hand-coins', 'keywords' => [
                        'loan', 'amortization', 'installment', 'home credit', 'pag-ibig', 'sss loan',
                        'mortgage', 'financing',
                    ]],
                    ['name' => 'Credit Card Payment', 'icon' => 'credit-card', 'keywords' => [
                        'credit card payment', 'card payment', 'statement balance',
                    ]],
                    ['name' => 'Taxes', 'icon' => 'file-text', 'keywords' => [
                        'bir', 'tax', 'income tax', 'business permit', 'documentary stamp', 'irs',
                    ]],
                ],
            ],
            [
                'name' => 'Personal',
                'icon' => 'user',
                'color' => '#8b5cf6',
                'categories' => [
                    ['name' => 'Personal Care', 'icon' => 'scissors', 'keywords' => [
                        'salon', 'barber', 'haircut', 'spa', 'massage', 'nail', 'skin care',
                        'cosmetics', 'sephora', 'the face shop', 'beauty',
                    ]],
                    ['name' => 'Pets', 'icon' => 'paw-print', 'keywords' => [
                        'pet', 'veterinary', 'vet', 'dog food', 'cat food', 'pet express',
                        'grooming', 'petco',
                    ]],
                    ['name' => 'Childcare', 'icon' => 'baby', 'keywords' => [
                        'diaper', 'formula milk', 'daycare', 'babysitter', 'toys', 'toy kingdom',
                    ]],
                    ['name' => 'Donations', 'icon' => 'heart-handshake', 'keywords' => [
                        'donation', 'charity', 'church', 'tithe', 'offering', 'red cross', 'fundraiser',
                    ]],
                ],
            ],
            [
                'name' => 'Other',
                'icon' => 'circle-ellipsis',
                'color' => '#94a3b8',
                'categories' => [
                    ['name' => 'Miscellaneous', 'icon' => 'circle-ellipsis', 'keywords' => []],
                    ['name' => 'Fees & Charges', 'icon' => 'badge-percent', 'keywords' => [
                        'penalty', 'late fee', 'surcharge', 'fine',
                    ]],
                ],
            ],
        ];
    }

    /**
     * @return array<int, array{name: string, icon: string, color: string, categories: array<int, array{name: string, icon: string, keywords: array<int, string>}>}>
     */
    private static function incomeGroups(): array
    {
        return [
            [
                'name' => 'Earnings',
                'icon' => 'briefcase',
                'color' => '#16a34a',
                'categories' => [
                    ['name' => 'Salary', 'icon' => 'wallet', 'keywords' => [
                        'salary', 'payroll', 'wage', 'compensation', 'net pay', 'direct deposit',
                    ]],
                    ['name' => 'Bonus', 'icon' => 'sparkles', 'keywords' => [
                        'bonus', '13th month', 'incentive', 'profit share', 'allowance',
                    ]],
                    ['name' => 'Freelance', 'icon' => 'laptop', 'keywords' => [
                        'freelance', 'upwork', 'fiverr', 'consulting', 'project fee', 'retainer',
                    ]],
                    ['name' => 'Commission', 'icon' => 'percent', 'keywords' => [
                        'commission', 'referral', 'affiliate',
                    ]],
                ],
            ],
            [
                'name' => 'Other Income',
                'icon' => 'piggy-bank',
                'color' => '#059669',
                'categories' => [
                    ['name' => 'Interest', 'icon' => 'trending-up', 'keywords' => [
                        'interest', 'time deposit', 'savings interest', 'money market',
                    ]],
                    ['name' => 'Dividends', 'icon' => 'chart-pie', 'keywords' => [
                        'dividend', 'stock', 'mutual fund', 'uitf', 'etf payout',
                    ]],
                    ['name' => 'Refunds', 'icon' => 'rotate-ccw', 'keywords' => [
                        'refund', 'rebate', 'reimbursement', 'cashback', 'return',
                    ]],
                    ['name' => 'Rental Income', 'icon' => 'building', 'keywords' => [
                        'rental income', 'tenant', 'lease income',
                    ]],
                    ['name' => 'Side Business', 'icon' => 'store', 'keywords' => [
                        'sales', 'business income', 'online selling', 'shop revenue',
                    ]],
                    ['name' => 'Gifts Received', 'icon' => 'gift', 'keywords' => [
                        'gift received', 'padala', 'remittance', 'allowance received',
                    ]],
                    ['name' => 'Other Income', 'icon' => 'circle-plus', 'keywords' => []],
                ],
            ],
        ];
    }
}
