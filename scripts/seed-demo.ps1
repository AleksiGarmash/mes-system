<#
  Заполняет локальный стенд демо-данными: спецификация качества, рецептура «Тестформ»,
  лоты сырья и два задания. Продукт и значения вымышленные.

  Запуск из корня проекта, после `docker compose up -d --build`:
      powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1

  Скрипт рассчитан на чистую базу. Повторный запуск упадёт на дубликатах кодов;
  чтобы начать заново: docker compose down -v; docker compose up -d --build
  Логин, пароль и секрет клиента — тестовые значения из docker/keycloak/mes-realm.json.
#>
$ErrorActionPreference = 'Stop'
$Api = 'http://localhost:8080/api'
$Root = Split-Path -Parent $PSScriptRoot

Write-Host 'Получаю токен admin.mes...'
$token = (Invoke-RestMethod -Method Post `
    -Uri 'http://localhost:8081/realms/mes/protocol/openid-connect/token' `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ grant_type = 'password'; client_id = 'mes-app'; client_secret = 'mes-app-secret';
             username = 'admin.mes'; password = 'admin123' }).access_token
$headers = @{ Authorization = "Bearer $token" }

function Post($path, $body) {
    $json = if ($body -is [string]) { $body } else { $body | ConvertTo-Json -Depth 10 }
    # Явно UTF-8: иначе Windows PowerShell 5 отправит кириллицу в другой кодировке
    Invoke-RestMethod -Method Post -Uri "$Api$path" -Headers $headers `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([Text.Encoding]::UTF8.GetBytes($json))
}

Write-Host 'Спецификация качества QC-TF-01...'
$spec = Post '/quality-specs' (Get-Content -Raw -Encoding UTF8 "$Root\docs\examples\demo-quality-spec.json")
Post "/quality-specs/$($spec.id)/activate" '{}' | Out-Null

Write-Host 'Рецептура TF-01...'
$recipe = Post '/recipes' (Get-Content -Raw -Encoding UTF8 "$Root\docs\examples\demo-recipe.json")
Post "/recipes/$($recipe.id)/activate" '{}' | Out-Null

Write-Host 'Лоты сырья...'
$today = Get-Date
$lots = @(
    @{ materialId = '11111111-1111-4111-8111-111111111111'; materialCode = 'Вода';        lotNo = 'RM-2026-0412'; supplier = 'Собственная водоподготовка'; quantity = 2400 }
    @{ materialId = '22222222-2222-4222-8222-222222222222'; materialCode = 'Компонент А'; lotNo = 'RM-2026-0390'; supplier = 'ООО «ХимСнаб»'; quantity = 120;  expiry = $today.AddDays(12).ToString('yyyy-MM-dd') }
    @{ materialId = '22222222-2222-4222-8222-222222222222'; materialCode = 'Компонент А'; lotNo = 'RM-2026-0455'; supplier = 'ООО «ХимСнаб»'; quantity = 640;  expiry = $today.AddDays(95).ToString('yyyy-MM-dd') }
    @{ materialId = '33333333-3333-4333-8333-333333333333'; materialCode = 'Компонент Б'; lotNo = 'RM-2026-0418'; supplier = 'АО «Полимер»';  quantity = 410;  expiry = $today.AddDays(40).ToString('yyyy-MM-dd') }
    @{ materialId = '55555555-5555-4555-8555-555555555555'; materialCode = 'Основа';      lotNo = 'RM-2026-0466'; supplier = 'АО «Кремний»';  quantity = 6000; expiry = $today.AddDays(250).ToString('yyyy-MM-dd') }
)
foreach ($lot in $lots) { Post '/stock/lots' $lot | Out-Null }

Write-Host 'Задания 245 и 246...'
foreach ($no in '245', '246') {
    Post '/orders' @{ orderNo = $no; recipeId = $recipe.id; plannedQty = 1000 } | Out-Null
}

Write-Host 'Готово. Откройте http://localhost:5173 и войдите как admin.mes / admin123.'
