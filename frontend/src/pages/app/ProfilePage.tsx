import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { ProfileInput } from '../../api/types'
import SessionBar from '../../components/SessionBar'

// 선택지는 docs/domain/matching.md 4장. 코드는 백엔드 enum 이름과 같다.
const GENDERS = [
  { code: 'FEMALE', label: '여성' },
  { code: 'MALE', label: '남성' },
]
const INTERESTS = [
  ['FOOD', '맛집'], ['TRAVEL', '여행'], ['MOVIE', '영화'], ['SPORTS', '운동'], ['READING', '독서'],
  ['EXHIBITION', '전시'], ['MUSIC', '음악'], ['GAME', '게임'], ['PET', '반려동물'], ['CAREER', '자기계발'],
].map(([code, label]) => ({ code, label }))
const TRAITS = [
  ['EMPATHETIC', '공감형'], ['PLANNER', '계획형'], ['ADVENTUROUS', '새로운 경험형'],
  ['HUMOROUS', '유머형'], ['LISTENER', '경청형'], ['CURIOUS', '호기심형'],
].map(([code, label]) => ({ code, label }))
const STYLES = [
  { code: 'QUIET', label: '조용한 대화' },
  { code: 'BALANCED', label: '균형' },
  { code: 'LIVELY', label: '활발한 대화' },
]
const PRICES = [
  { code: 'UNDER_20K', label: '2만 원 미만' },
  { code: '20K_40K', label: '2~4만 원' },
  { code: 'OVER_40K', label: '4만 원 이상' },
]
const MBTIS = [
  'ISTJ', 'ISFJ', 'INFJ', 'INTJ', 'ISTP', 'ISFP', 'INFP', 'INTP',
  'ESTP', 'ESFP', 'ENFP', 'ENTP', 'ESTJ', 'ESFJ', 'ENFJ', 'ENTJ',
]

const EMPTY: ProfileInput = {
  name: null, phone: null, birthYear: null, gender: null, job: null, mbti: null, interests: [], selfTraits: [], preferredTraits: [],
  conversationStyle: null, priceRange: null, foodRestrictions: null, allergies: null,
  privacyAgreed: false, policyAgreed: false,
}

// 프로필 입력·수정 (/app/profile, ACC-02~05). 섹션 3개, 칩 선택식. 디자인은 9.x에서 입힌다.
export default function ProfilePage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = params.get('next') ?? '/app'
  const profile = useQuery({ queryKey: ['my-profile'], queryFn: api.myProfile })
  const [form, setForm] = useState<ProfileInput>(EMPTY)

  useEffect(() => {
    if (profile.data) {
      const { complete: _complete, privacyAgreedAt, policyAgreedAt, ...rest } = profile.data
      setForm({ ...rest, privacyAgreed: privacyAgreedAt != null, policyAgreed: policyAgreedAt != null })
    }
  }, [profile.data])

  const save = useMutation({
    mutationFn: () => api.saveProfile(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-profile'] })
      navigate(next)
    },
  })

  const set = <K extends keyof ProfileInput>(key: K, value: ProfileInput[K]) => setForm((f) => ({ ...f, [key]: value }))
  // 다중 선택 칩: 최대 개수를 넘으면 더 고르지 못한다
  const toggle = (key: 'interests' | 'selfTraits' | 'preferredTraits', code: string, max: number) =>
    setForm((f) => {
      const cur = f[key]
      if (cur.includes(code)) return { ...f, [key]: cur.filter((c) => c !== code) }
      return cur.length >= max ? f : { ...f, [key]: [...cur, code] }
    })

  const ready =
    !!form.name?.trim() && !!form.phone?.trim() && form.birthYear != null && form.gender != null && form.interests.length >= 1 && form.selfTraits.length >= 1 &&
    form.priceRange != null && form.privacyAgreed && form.policyAgreed

  return (
    <main className="mx-auto min-h-screen max-w-md bg-paper p-[18px] pb-10 text-ink">
      <header className="flex items-center justify-between gap-2">
        <Link to="/app" className="flex min-h-[44px] items-center text-sm font-bold text-accent">
          ← 홈
        </Link>
        <SessionBar />
      </header>
      <div className="mt-3 font-display text-[17px] text-accent italic">About you.</div>
      <h1 className="font-serif text-[28px] leading-tight font-black tracking-[-1px]">내 프로필</h1>
      {params.get('next') && (
        <p className="mt-1 text-sm font-bold text-accent">신청하려면 프로필을 먼저 채워주세요. 1~2분이면 끝나요.</p>
      )}
      {profile.isPending && <p className="mt-4 text-sm text-muted">불러오는 중…</p>}
      {profile.isError && <p className="mt-4 text-sm font-medium text-accent">{errorMessage(profile.error)}</p>}

      <Section title="1. 기본 정보">
        <Label>이름</Label>
        <input
          type="text"
          value={form.name ?? ''}
          onChange={(e) => set('name', e.target.value || null)}
          maxLength={30}
          placeholder="이름"
          autoComplete="name"
          className="min-h-[44px] w-full rounded-xl border border-line bg-card px-3 text-sm"
        />
        <Label>연락처</Label>
        <input
          type="tel"
          inputMode="tel"
          value={form.phone ?? ''}
          onChange={(e) => set('phone', e.target.value || null)}
          maxLength={15}
          placeholder="010-1234-5678"
          autoComplete="tel"
          className="min-h-[44px] w-full rounded-xl border border-line bg-card px-3 text-sm"
        />
        <Label>출생연도 (만 19~45세)</Label>
        <input
          type="number"
          inputMode="numeric"
          value={form.birthYear ?? ''}
          onChange={(e) => set('birthYear', e.target.value ? Number(e.target.value) : null)}
          placeholder="예: 1997"
          className="min-h-[44px] w-full rounded-xl border border-line bg-card px-3 text-sm"
        />
        <Label>성별</Label>
        <Chips options={GENDERS} selected={form.gender ? [form.gender] : []} onToggle={(c) => set('gender', c)} />
        <Label>직업 (선택)</Label>
        <input
          type="text"
          maxLength={50}
          value={form.job ?? ''}
          onChange={(e) => set('job', e.target.value || null)}
          className="min-h-[44px] w-full rounded-xl border border-line bg-card px-3 text-sm"
        />
      </Section>

      <Section title="2. 매칭 선호">
        <Label>관심사 (1~5개) · {form.interests.length}/5</Label>
        <Chips options={INTERESTS} selected={form.interests} onToggle={(c) => toggle('interests', c, 5)} />
        <Label>나의 성향 (1~3개) · {form.selfTraits.length}/3</Label>
        <Chips options={TRAITS} selected={form.selfTraits} onToggle={(c) => toggle('selfTraits', c, 3)} />
        <Label>만나고 싶은 상대 성향 (선택, 최대 3개)</Label>
        <Chips options={TRAITS} selected={form.preferredTraits} onToggle={(c) => toggle('preferredTraits', c, 3)} />
        <Label>대화 스타일 (선택)</Label>
        <Chips
          options={STYLES}
          selected={form.conversationStyle ? [form.conversationStyle] : []}
          onToggle={(c) => set('conversationStyle', form.conversationStyle === c ? null : c)}
        />
        <Label>MBTI (선택)</Label>
        <Chips
          options={MBTIS.map((m) => ({ code: m, label: m }))}
          selected={form.mbti ? [form.mbti] : []}
          onToggle={(c) => set('mbti', form.mbti === c ? null : c)}
        />
      </Section>

      <Section title="3. 식사 조건·동의">
        <Label>희망 식사 가격대</Label>
        <Chips options={PRICES} selected={form.priceRange ? [form.priceRange] : []} onToggle={(c) => set('priceRange', c)} />
        <Label>못 먹는 음식 (선택)</Label>
        <input
          type="text"
          maxLength={200}
          value={form.foodRestrictions ?? ''}
          onChange={(e) => set('foodRestrictions', e.target.value || null)}
          className="min-h-[44px] w-full rounded-xl border border-line bg-card px-3 text-sm"
        />
        <Label>알레르기 (선택)</Label>
        <input
          type="text"
          maxLength={200}
          value={form.allergies ?? ''}
          onChange={(e) => set('allergies', e.target.value || null)}
          className="min-h-[44px] w-full rounded-xl border border-line bg-card px-3 text-sm"
        />
        <label className="mt-4 flex items-start gap-2 text-sm">
          <input type="checkbox" checked={form.privacyAgreed} onChange={(e) => set('privacyAgreed', e.target.checked)} className="mt-1 h-5 w-5 accent-[#7e2a27]" />
          <span>[필수] 개인정보 수집·이용에 동의합니다. (매칭 전에는 다른 참가자에게 공개되지 않아요)</span>
        </label>
        <label className="mt-2 flex items-start gap-2 text-sm">
          <input type="checkbox" checked={form.policyAgreed} onChange={(e) => set('policyAgreed', e.target.checked)} className="mt-1 h-5 w-5 accent-[#7e2a27]" />
          <span>[필수] 운영정책에 동의합니다. 진행 2일 전부터 환불 불가, 당일 취소 및 단체방 퇴장 불가.</span>
        </label>
      </Section>

      {save.isError && <p className="mt-4 text-sm font-medium text-accent">{errorMessage(save.error)}</p>}
      <button
        type="button"
        disabled={!ready || save.isPending}
        onClick={() => save.mutate()}
        className="mt-4 w-full min-h-[52px] rounded-xl bg-ink text-[15px] font-bold text-cream disabled:opacity-40"
      >
        {save.isPending ? '저장 중…' : '저장하기'}
      </button>
    </main>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mt-6 space-y-2">
      <h2 className="border-b border-line pb-1.5 font-display text-[17px] italic">{title}</h2>
      {children}
    </section>
  )
}

function Label({ children }: { children: React.ReactNode }) {
  return <p className="pt-2 text-sm font-bold">{children}</p>
}

function Chips({
  options,
  selected,
  onToggle,
}: {
  options: { code: string; label: string }[]
  selected: string[]
  onToggle: (code: string) => void
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {options.map((o) => (
        <button
          key={o.code}
          type="button"
          onClick={() => onToggle(o.code)}
          className={`min-h-[44px] rounded-xl border border-line px-3 text-sm font-bold ${
            selected.includes(o.code) ? 'bg-ink text-cream' : 'bg-card'
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}
