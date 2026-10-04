import { useEffect, useState } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'

// 화면 8개(7장). 사진 등록·작업 현황·검수는 문제은행을 채우고, 나머지는 문제은행만으로 문제지를 만든다.
const screens = [
  { path: '/upload', title: '사진 등록' },
  { path: '/jobs', title: '작업 현황' },
  { path: '/review', title: '검수' },
  { path: '/bank', title: '문제은행' },
  { path: '/compose', title: '문제지 만들기' },
  { path: '/worksheets', title: '저장된 문제지' },
  { path: '/trash', title: '휴지통' },
  { path: '/settings', title: '설정' },
]

export default function App() {
  const [server, setServer] = useState('확인 중')

  useEffect(() => {
    fetch('/api/health')
      .then((res) => res.json())
      .then((body: { status: string; version: string }) => setServer(`실행 중 · v${body.version}`))
      .catch(() => setServer('서버에 연결할 수 없습니다'))
  }, [])

  return (
    <div className="app">
      <nav className="nav">
        <strong>수학 문제지</strong>
        {screens.map((s) => (
          <NavLink key={s.path} to={s.path}>
            {s.title}
          </NavLink>
        ))}
        <span className="server-status">{server}</span>
      </nav>
      <main className="main">
        <Routes>
          <Route path="/" element={<Navigate to="/bank" replace />} />
          {screens.map((s) => (
            <Route key={s.path} path={s.path} element={<h1>{s.title}</h1>} />
          ))}
        </Routes>
      </main>
    </div>
  )
}
