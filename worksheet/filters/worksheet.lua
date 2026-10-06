--[[
문제지 Lua 필터 (설계서 8장 "문제지 생성 순서" 3, docs/manuscript-format.md "Lua 필터가 하는 일")

입력: 원고 마크다운(problem / choices / bogi / answer / group 구역)
출력: 형식 파일의 필수 스타일(custom-style)을 입힌 블록

지키는 것
  - 본문 글자(Str, Math, Image, Table 내용)는 바꾸지 않는다.
    붙이는 것은 번호, Word 탭, <보 기> 제목, 정답 줄, 정답표, 빈 공간뿐이다.
  - 줄을 나누는 위치의 SoftBreak(원고의 줄바꿈)는 단락 경계나 Word 탭으로 바뀐다(공백 문자만 영향).

정답지: pandoc ... -M answers=true
]]

-- 형식 파일(build_template.py)과 맞춘 치수
local TWIP_PER_CM = 567
-- 표 너비 = 본문 너비 × 44%(설계서 9장, A4 2단에서 약 7.7cm로 한 단 안에 들어감).
-- Pandoc은 열 너비를 비율로만 쓰고(tblW type=pct), Word는 그 비율을 "본문"이 아니라
-- "단" 너비에 곱한다(미리보기로 확인: 44%를 그대로 넣으면 8.2cm × 44% = 3.6cm).
-- 그래서 본문 44%에 해당하는 값을 단 기준 비율로 바꿔 넣는다.
local BODY_TWIP, COLUMN_TWIP = 9866, 4649   -- build_template.py의 BODY_W, COL_W
local TABLE_RATIO = 0.44 * BODY_TWIP / COLUMN_TWIP   -- ≈ 0.934 (단 기준)
-- 가장 긴 보기 너비(칸) 기준. 형식마다 단 너비·글자 크기가 달라 형식 설정에서 -M choices5-max=5 -M choices3-max=10으로 넘긴다.
-- 넘기지 않으면 설계서 8장 기본값(9/15)을 쓴다.
local CHOICES5_MAX = 9
local CHOICES3_MAX = 15

local ANSWERS = false
local answer_rows = {}            -- 정답표: { num=, inlines=, source= }

local TAB = function() return pandoc.RawInline('openxml', '<w:r><w:tab/></w:r>') end

local function styled_div(style, blocks)
  return pandoc.Div(blocks, pandoc.Attr('', {}, { { 'custom-style', style } }))
end

local function styled_span(style, inlines)
  return pandoc.Span(inlines, pandoc.Attr('', {}, { { 'custom-style', style } }))
end

local function is_blank(el)
  return el.t == 'Space' or el.t == 'SoftBreak' or el.t == 'LineBreak'
end

local function trim(inlines)
  local out = pandoc.List(inlines)
  while #out > 0 and is_blank(out[1]) do out:remove(1) end
  while #out > 0 and is_blank(out[#out]) do out:remove(#out) end
  return out
end

------------------------------------------------------------------------
-- 보기 너비 계산
------------------------------------------------------------------------
local function char_width(cp)
  if (cp >= 0x1100 and cp <= 0x11FF) or (cp >= 0x3130 and cp <= 0x318F)
      or (cp >= 0xAC00 and cp <= 0xD7A3) or (cp >= 0x4E00 and cp <= 0x9FFF) then
    return 2
  end
  return 1
end

local function text_width(s)
  local w = 0
  for _, cp in utf8.codes(s) do w = w + char_width(cp) end
  return w
end

-- 수식: LaTeX 명령어(\frac 등)와 중괄호, 공백, 위·아래 첨자 기호를 뺀 글자 수
local function tex_width(s)
  s = s:gsub('\\[{}]', 'X')          -- \{ \} 는 화면에 보이는 괄호
  s = s:gsub('\\[A-Za-z]+', '')       -- 명령어
  s = s:gsub('\\.', '')               -- \, \; \! 같은 간격 명령
  s = s:gsub('[{}%s%^_&]', '')
  return text_width(s)
end

local function is_circled(cp) return cp >= 0x2460 and cp <= 0x2473 end

local function first_cp(s)
  for _, cp in utf8.codes(s) do return cp end
end

-- 보기 한 개(①~⑤ 표시 포함 인라인 목록)의 너비. 맨 앞 표시와 그 뒤 공백은 세지 않는다.
local function choice_width(inlines)
  local w = 0
  local skip_space = false
  for i, el in ipairs(inlines) do
    if el.t == 'Str' then
      local s = el.text
      if i == 1 and first_cp(s) and is_circled(first_cp(s)) then
        s = s:sub(utf8.offset(s, 2))
        skip_space = (s == '')
      end
      w = w + text_width(s)
    elseif el.t == 'Space' then
      if skip_space then skip_space = false else w = w + 1 end
    elseif el.t == 'Math' then
      skip_space = false
      w = w + tex_width(el.text)
    else
      skip_space = false
      w = w + text_width(pandoc.utils.stringify(el))
    end
  end
  return w
end

------------------------------------------------------------------------
-- 구역 처리
------------------------------------------------------------------------

-- 단락 안 줄바꿈(SoftBreak/LineBreak) 기준으로 줄 목록을 만든다.
local function split_lines(blocks)
  local lines = pandoc.List()
  for _, b in ipairs(blocks) do
    if b.t == 'Para' or b.t == 'Plain' then
      local cur = pandoc.List()
      for _, el in ipairs(b.content) do
        if el.t == 'SoftBreak' or el.t == 'LineBreak' then
          if #trim(cur) > 0 then lines:insert(trim(cur)) end
          cur = pandoc.List()
        else
          cur:insert(el)
        end
      end
      if #trim(cur) > 0 then lines:insert(trim(cur)) end
    end
  end
  return lines
end

-- 정답 구역에서 객관식 정답 번호(1~5)를 얻는다. 아니면 nil.
local function choice_index(answer_inlines)
  if not answer_inlines then return nil end
  local s = pandoc.utils.stringify(answer_inlines):gsub('%s', '')
  if utf8.len(s) == 1 then
    local cp = first_cp(s)
    if is_circled(cp) then return cp - 0x2460 + 1 end
  end
  return nil
end

local function make_choices(div, correct)
  local items = split_lines(div.content)
  local maxw = 0
  for _, it in ipairs(items) do maxw = math.max(maxw, choice_width(it)) end
  local style, per_line
  if maxw <= CHOICES5_MAX then style, per_line = 'Choices5', 5
  elseif maxw <= CHOICES3_MAX then style, per_line = 'Choices3', 3
  else style, per_line = 'Choices1', 1 end

  local out = pandoc.List()
  local cur = pandoc.List()
  for i, it in ipairs(items) do
    local content = it
    if ANSWERS and correct == i then
      content = pandoc.List({ styled_span('CorrectChoice', it) })
    end
    if #cur > 0 then cur:insert(TAB()) end
    cur:extend(content)
    if i % per_line == 0 or i == #items then
      out:insert(styled_div(style, { pandoc.Para(cur) }))
      cur = pandoc.List()
    end
  end
  return out
end

local function make_bogi(div)
  local out = pandoc.List()
  out:insert(styled_div('BogiTitle', { pandoc.Para({ pandoc.Str('<보'), pandoc.Space(), pandoc.Str('기>') }) }))
  for _, line in ipairs(split_lines(div.content)) do
    out:insert(styled_div('BogiBox', { pandoc.Para(line) }))
  end
  return out
end

local function is_image_para(b)
  if b.t ~= 'Para' and b.t ~= 'Plain' then return false end
  local has = false
  for _, el in ipairs(b.content) do
    if el.t == 'Image' then has = true
    elseif not is_blank(el) then return false end
  end
  return has
end

-- 표: 열 너비를 본문 너비 44% / 열 수로 다시 정한다.
local function fit_table(tbl)
  local n = #tbl.colspecs
  if n == 0 then return tbl end
  for i, cs in ipairs(tbl.colspecs) do
    tbl.colspecs[i] = { cs[1], TABLE_RATIO / n }
  end
  return tbl
end

-- 단락을 블록 수식 앞뒤로 나눈다(Pandoc도 Word에서 같은 위치로 단락을 나눈다).
-- 반환: { {math=bool, inlines=List}, ... }
local function split_display(inlines)
  local parts = {}
  local cur = pandoc.List()
  for _, el in ipairs(inlines) do
    if el.t == 'Math' and el.mathtype == 'DisplayMath' then
      if #trim(cur) > 0 then table.insert(parts, { math = false, inlines = trim(cur) }) end
      table.insert(parts, { math = true, inlines = pandoc.List({ el }) })
      cur = pandoc.List()
    else
      cur:insert(el)
    end
  end
  if #trim(cur) > 0 then table.insert(parts, { math = false, inlines = trim(cur) }) end
  return parts
end

local function space_block(cm, keep_next)
  local twip = math.floor(cm * TWIP_PER_CM + 0.5)
  if twip < 20 then twip = 20 end
  local keep = keep_next and '<w:keepNext/>' or ''
  return pandoc.RawBlock('openxml',
    '<w:p><w:pPr>' .. keep ..
    '<w:spacing w:before="0" w:after="0" w:line="' .. twip .. '" w:lineRule="exact"/>' ..
    '<w:rPr><w:sz w:val="2"/></w:rPr></w:pPr></w:p>')
end

local function parse_cm(s)
  if not s then return nil end
  local v = s:match('^%s*([%d%.]+)%s*cm%s*$') or s:match('^%s*([%d%.]+)%s*$')
  return v and tonumber(v) or nil
end

local function number_prefix(label)
  return { styled_span('ProblemNumber', { pandoc.Str(label) }), TAB() }
end

-- problem 구역 → 블록 목록
local function make_problem(div, keep_after)
  local num = div.attributes['num'] or '?'
  local source = div.attributes['source']
  local space_cm = parse_cm(div.attributes['space'])

  -- 정답 먼저 찾는다(보기 강조에 필요).
  local answer_inlines = nil
  for _, b in ipairs(div.content) do
    if b.t == 'Div' and b.classes:includes('answer') then
      answer_inlines = pandoc.List()
      for _, ab in ipairs(b.content) do
        if ab.content then
          if #answer_inlines > 0 then answer_inlines:insert(pandoc.Space()) end
          answer_inlines:extend(trim(ab.content))
        end
      end
    end
  end
  table.insert(answer_rows, { num = num, inlines = answer_inlines, source = source })
  local correct = choice_index(answer_inlines)

  local out = pandoc.List()
  local numbered = false

  local function problem_para(inlines, is_math)
    local content = pandoc.List()
    if not numbered then
      content:extend(number_prefix(num .. '.'))
      numbered = true
    elseif not is_math then
      content:insert(TAB())       -- 이어지는 단락도 번호 칸 뒤에서 시작
    end
    content:extend(inlines)
    out:insert(styled_div('Problem', { pandoc.Para(content) }))
  end

  for _, b in ipairs(div.content) do
    if b.t == 'Div' and b.classes:includes('choices') then
      out:extend(make_choices(b, correct))
    elseif b.t == 'Div' and b.classes:includes('bogi') then
      out:extend(make_bogi(b))
    elseif b.t == 'Div' and b.classes:includes('answer') then
      -- 정답 줄은 아래에서 한 번만 넣는다.
    elseif is_image_para(b) then
      if not numbered then problem_para({}, false) end
      out:insert(styled_div('Figure', { pandoc.Para(b.content) }))
    elseif b.t == 'Para' or b.t == 'Plain' then
      for _, part in ipairs(split_display(b.content)) do
        problem_para(part.inlines, part.math)
      end
    elseif b.t == 'Table' then
      if not numbered then problem_para({}, false) end
      out:insert(fit_table(b))
    else
      out:insert(b)
    end
  end
  if not numbered then problem_para({}, false) end

  if ANSWERS then
    local line
    if answer_inlines and #answer_inlines > 0 then
      line = pandoc.List({ pandoc.Str('정답'), pandoc.Space() })
      line:extend(answer_inlines)
    else
      local text = '정답 미입력'
      if source and source ~= '' then text = text .. ' · 출처: ' .. source end
      line = pandoc.List({ pandoc.Str(text) })
    end
    out:insert(styled_div('Answer', { pandoc.Para(line) }))
  end

  out:insert(space_block(space_cm or 0, keep_after))
  return out
end

-- group 구역 → 공통 지문(GroupStem) + 하위 문제. 끝 문제 전까지 모두 "다음 단락과 함께".
local function make_group(div)
  local range = div.attributes['range']
  local out = pandoc.List()
  local problems = {}
  for _, b in ipairs(div.content) do
    if b.t == 'Div' and b.classes:includes('problem') then table.insert(problems, b) end
  end
  local labeled = false
  local done = 0
  for _, b in ipairs(div.content) do
    if b.t == 'Div' and b.classes:includes('problem') then
      done = done + 1
      out:extend(make_problem(b, done < #problems))
    elseif is_image_para(b) then
      out:insert(styled_div('Figure', { pandoc.Para(b.content) }))
    elseif b.t == 'Para' or b.t == 'Plain' then
      for _, part in ipairs(split_display(b.content)) do
        local content = pandoc.List()
        if not labeled and range then
          content:insert(styled_span('ProblemNumber', { pandoc.Str('[' .. range .. ']') }))
          content:insert(pandoc.Space())
          labeled = true
        end
        content:extend(part.inlines)
        out:insert(styled_div('GroupStem', { pandoc.Para(content) }))
      end
    elseif b.t == 'Table' then
      out:insert(fit_table(b))
    else
      out:insert(b)
    end
  end
  return out
end

------------------------------------------------------------------------
-- 정답표(정답지 첫 쪽)
------------------------------------------------------------------------
local function cell(inlines) return { pandoc.Plain(inlines) } end

local function answer_table()
  local pairs_per_row = 2
  local aligns, widths, headers = {}, {}, {}
  for _ = 1, pairs_per_row do
    table.insert(aligns, pandoc.AlignCenter); table.insert(widths, 0.12)
    table.insert(aligns, pandoc.AlignCenter); table.insert(widths, TABLE_RATIO / pairs_per_row - 0.12)
    table.insert(headers, cell({ pandoc.Str('번호') }))
    table.insert(headers, cell({ pandoc.Str('정답') }))
  end
  local rows = {}
  local row = {}
  for _, r in ipairs(answer_rows) do
    local ans
    if r.inlines and #r.inlines > 0 then
      ans = r.inlines
    else
      local text = '미입력'
      if r.source and r.source ~= '' then text = text .. ' · 출처: ' .. r.source end
      ans = { pandoc.Str(text) }
    end
    table.insert(row, cell({ pandoc.Str(r.num) }))
    table.insert(row, cell(ans))
    if #row == pairs_per_row * 2 then table.insert(rows, row); row = {} end
  end
  if #row > 0 then
    while #row < pairs_per_row * 2 do table.insert(row, cell({})) end
    table.insert(rows, row)
  end
  local tbl = pandoc.utils.from_simple_table(pandoc.SimpleTable({}, aligns, widths, headers, rows))
  tbl.attr = pandoc.Attr('', {}, { { 'custom-style', 'AnswerTable' } })
  return tbl
end

------------------------------------------------------------------------

function Pandoc(doc)
  local a = doc.meta.answers
  ANSWERS = (a == true) or (a ~= nil and pandoc.utils.stringify(a) == 'true')
  CHOICES5_MAX = tonumber(doc.meta['choices5-max'] and pandoc.utils.stringify(doc.meta['choices5-max'])) or CHOICES5_MAX
  CHOICES3_MAX = tonumber(doc.meta['choices3-max'] and pandoc.utils.stringify(doc.meta['choices3-max'])) or CHOICES3_MAX
  answer_rows = {}

  local body = pandoc.List()
  for _, b in ipairs(doc.blocks) do
    if b.t == 'Div' and b.classes:includes('problem') then
      body:extend(make_problem(b, false))
    elseif b.t == 'Div' and b.classes:includes('group') then
      body:extend(make_group(b))
    else
      body:insert(b)
    end
  end

  if ANSWERS then
    local head = pandoc.List()
    head:insert(styled_div('Answer', { pandoc.Para({ pandoc.Str('빠른'), pandoc.Space(), pandoc.Str('정답표') }) }))
    head:insert(answer_table())
    head:insert(pandoc.RawBlock('openxml', '<w:p><w:r><w:br w:type="page"/></w:r></w:p>'))
    head:extend(body)
    body = head
  end

  doc.blocks = body
  return doc
end
