import DeployStatus from '../../components/DeployStatus'

// 참가자 앱 홈 (/app). 모바일 우선.
export default function ParticipantHomePage() {
  return (
    <main className="mx-auto min-h-screen max-w-md p-4">
      <h1 className="text-xl font-bold">우연한 식탁</h1>
      <p className="mt-2 text-sm text-gray-600">참가자 앱</p>
      <DeployStatus />
    </main>
  )
}
