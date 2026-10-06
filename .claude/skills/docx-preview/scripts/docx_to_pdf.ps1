# Word COM으로 docx를 PDF로 저장한다.
# 사용법: powershell -NoProfile -ExecutionPolicy Bypass -File docx_to_pdf.ps1 -Docx in.docx -Pdf out.pdf
param(
  [Parameter(Mandatory = $true)][string]$Docx,
  [Parameter(Mandatory = $true)][string]$Pdf
)
$ErrorActionPreference = 'Stop'
$docxFull = (Resolve-Path -LiteralPath $Docx).Path
$pdfFull = [System.IO.Path]::GetFullPath($Pdf)
$word = $null
$doc = $null
try {
  $word = New-Object -ComObject Word.Application
  $word.Visible = $false
  $word.DisplayAlerts = 0
  # Open(FileName, ConfirmConversions, ReadOnly, AddToRecentFiles)
  $doc = $word.Documents.Open($docxFull, $false, $true, $false)
  # 17 = wdExportFormatPDF
  $doc.ExportAsFixedFormat($pdfFull, 17)
  Write-Output "PDF: $pdfFull"
}
finally {
  if ($doc -ne $null) { $doc.Close(0) | Out-Null }
  if ($word -ne $null) { $word.Quit() | Out-Null; [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null }
}
