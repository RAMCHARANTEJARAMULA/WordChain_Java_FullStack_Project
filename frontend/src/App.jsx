import { useEffect, useState } from 'react'
import { ArrowRight, Copy, Gamepad2, Users, Trophy, Sparkles, RefreshCw, LogOut } from 'lucide-react'

const API = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api').replace(/\/$/,'')
async function request(path, options={}) {
  const res = await fetch(`${API}${path}`, {headers:{'Content-Type':'application/json'}, ...options})
  const data = await res.json().catch(()=>({}))
  if (!res.ok) throw new Error(data.error || 'Something went wrong')
  return data
}

export default function App() {
  const [name,setName]=useState('')
  const [code,setCode]=useState('')
  const [joinCode,setJoinCode]=useState('')
  const [room,setRoom]=useState(null)
  const [word,setWord]=useState('')
  const [busy,setBusy]=useState(false)
  const [error,setError]=useState('')
  const [notice,setNotice]=useState('')
  const [demo,setDemo]=useState(false)

  async function createRoom(e) {
    e?.preventDefault(); setError(''); setNotice('')
    if(!name.trim()) return setError('Enter your player name first.')
    setBusy(true)
    try { const r=await request('/rooms',{method:'POST',body:JSON.stringify({playerName:name.trim()})}); setRoom(r);setCode(r.code);setDemo(false) }
    catch(err){setError(err.message)}
    finally{setBusy(false)}
  }
  async function joinRoom(e) {
    e?.preventDefault(); setError('');setNotice('')
    if(!name.trim()) return setError('Enter your player name first.')
    if(!joinCode.trim()) return setError('Enter the referral code.')
    setBusy(true)
    try {const r=await request(`/rooms/${encodeURIComponent(joinCode.trim())}/join`,{method:'POST',body:JSON.stringify({playerName:name.trim()})});setRoom(r);setCode(r.code);setDemo(false)}
    catch(err){setError(err.message)}
    finally{setBusy(false)}
  }
  async function refresh(silent=false) {
    if(!code || demo)return
    try { const r=await request(`/rooms/${encodeURIComponent(code)}`);setRoom(r);if(!silent)setNotice('Game refreshed') }
    catch(err){if(!silent)setError(err.message)}
  }
  useEffect(()=>{if(!room||demo)return;const id=setInterval(()=>refresh(true),3000);return()=>clearInterval(id)},[room?.code,demo])
  async function play(e) {
    e.preventDefault();setError('');setNotice('')
    if(!word.trim())return
    setBusy(true)
    try {const r=await request(`/rooms/${code}/words`,{method:'POST',body:JSON.stringify({playerName:name.trim(),word:word.trim()})});setRoom(r);setWord('');setNotice('Nice move! Word added.')}
    catch(err){setError(err.message)}
    finally{setBusy(false)}
  }
  async function copyCode(){try{await navigator.clipboard.writeText(code);setNotice('Referral code copied!')}catch{setNotice(`Share this code: ${code}`)}}
  function demoGame(){
    const players=[{name:name.trim()||'You',score:5,seat:0},{name:'Player 2',score:0,seat:1}]
    setName(name.trim()||'You');setCode('DEMO42');setRoom({code:'DEMO42',status:'PLAYING',players,words:[{id:1,word:'apple',playerName:players[0].name}],currentPlayer:'Player 2',nextLetter:'E'});setDemo(true);setError('');setNotice('Demo mode: play locally to preview the game.')
  }
  async function demoPlay(e){
    e.preventDefault(); const w=word.trim().toLowerCase()
    if(!w)return
    if(!w.startsWith(room.nextLetter.toLowerCase()))return setError(`Your word must start with ${room.nextLetter}.`)
    if(room.words.some(x=>x.word===w))return setError('That word was already used.')
    const ps=room.players; const player=room.currentPlayer
    const words=[...room.words,{id:Date.now(),word:w,playerName:player}]
    const idx=ps.findIndex(p=>p.name===player)
    const players=ps.map(p=>p.name===player?{...p,score:p.score+w.length}:p)
    setRoom({...room,words,players,currentPlayer:ps[(idx+1)%ps.length].name,nextLetter:w.slice(-1).toUpperCase()});setWord('');setError('')
  }
  async function submitWord(e){if(demo)return demoPlay(e);return play(e)}
  function leave(){setRoom(null);setCode('');setJoinCode('');setWord('');setError('');setNotice('')}
  const current=room?.players?.find(p=>p.name===room.currentPlayer)
  return <main className="app-shell">
    <header className="topbar"><a className="brand" href="#"><span className="brand-icon"><Gamepad2 size={22}/></span> word<span>chain</span></a><span className="online"><i/> Multiplayer word game</span></header>
    {!room ? <section className="hero">
      <div className="hero-copy"><div className="eyebrow"><Sparkles size={15}/> THINK FAST. CHAIN WORDS.</div>
        <h1>One word.<br/><span>One more.</span><br/>Your move.</h1>
        <p className="intro">A tiny word game with big brain energy. Connect words, challenge friends, and keep the chain alive.</p>
        <div className="feature-row"><span><Users size={16}/> Up to 4 players</span><span><Trophy size={16}/> Score points</span></div>
        <div className="letter-art" aria-label="Word chain example"><div className="letter-card purple">A</div><ArrowRight/><div className="letter-card pink">E</div><ArrowRight/><div className="letter-card yellow">E</div><ArrowRight/><div className="letter-card green">R</div></div>
      </div>
      <div className="join-card"><div className="card-symbol"><Gamepad2 size={25}/></div><h2>Ready to play?</h2><p>Choose a name, then create a room or join your friends.</p>
        <label>Your player name</label><input value={name} onChange={e=>setName(e.target.value)} maxLength={24} placeholder="e.g. WordWizard" onKeyDown={e=>e.key==='Enter'&&createRoom(e)}/>
        <button className="primary" onClick={createRoom} disabled={busy}>{busy?'Creating…':'Create a new room'} <ArrowRight size={17}/></button>
        <div className="or"><span/>OR<span/></div>
        <form onSubmit={joinRoom}><label>Have a referral code?</label><div className="join-line"><input value={joinCode} onChange={e=>setJoinCode(e.target.value.toUpperCase())} maxLength={6} placeholder="ABC123"/><button className="join-btn" disabled={busy}>Join</button></div></form>
        <button className="demo-btn" onClick={demoGame}>Try a demo without backend ↗</button>
        {error&&<div className="error">{error}</div>}
      </div>
    </section> : <section className="game-layout">
      <div className="game-heading"><div><div className="eyebrow"><span className="live-dot"/> ROOM {room.code}</div><h1>Keep the chain <span>alive.</span></h1><p>Every word starts where the last one ended.</p></div><button className="ghost" onClick={leave}><LogOut size={16}/> Leave</button></div>
      <div className="game-grid"><section className="play-card">
        <div className="room-strip"><div><span className="tiny-label">REFERRAL CODE</span><strong className="room-code">{room.code}</strong></div><button className="copy-btn" onClick={copyCode}><Copy size={16}/> Copy code</button></div>
        <div className="turn-banner"><div className="turn-label">CURRENT TURN</div><strong>{room.currentPlayer||'Waiting for players'}</strong><span>{room.currentPlayer?.toLowerCase()===name.trim().toLowerCase()?'Your turn — make it count!':`Waiting for ${room.currentPlayer||'players'}…`}</span></div>
        <div className="letter-prompt"><span>YOUR WORD STARTS WITH</span><strong>{room.nextLetter==='Any letter'?'ANY':room.nextLetter}</strong></div>
        <form className="word-form" onSubmit={submitWord}><input value={word} onChange={e=>setWord(e.target.value.replace(/[^a-zA-Z]/g,''))} placeholder="Type your word…" maxLength={24}/><button className="primary" disabled={busy||(!demo&&room.currentPlayer?.toLowerCase()!==name.trim().toLowerCase())}>{busy?'Sending…':'Play word'} <ArrowRight size={17}/></button></form>
        <div className="hint">2–24 letters · No spaces · No repeated words</div>
        {error&&<div className="error">{error}</div>}{notice&&<div className="notice">{notice}</div>}
        <div className="history-head"><h3>Word trail <span>{room.words.length}</span></h3><button className="icon-btn" onClick={()=>refresh()} title="Refresh"><RefreshCw size={16}/></button></div>
        <div className="word-trail">{room.words.length===0?<p className="empty">Your first word starts the chain. Go on!</p>:[...room.words].reverse().map((w,i)=><div className="word-row" key={w.id}><span className="word-number">{room.words.length-i}</span><strong>{w.word}</strong><span className="word-author">{w.playerName}</span><span className="word-points">+{w.word.length}</span></div>)}</div>
      </section><aside className="side-column"><section className="side-card"><div className="side-title"><Users size={17}/><h3>Players</h3><span>{room.players.length}/4</span></div>{room.players.map((p,i)=><div className="player-row" key={p.name}><div className={`avatar avatar-${i%4}`}>{p.name.slice(0,1).toUpperCase()}</div><div className="player-info"><strong>{p.name}</strong><small>{p.name===room.currentPlayer?'Playing now':'In the room'}</small></div><b>{p.score}<small> pts</small></b></div>)}{room.players.length<4&&<p className="invite-note">Share the room code to invite up to {4-room.players.length} more player(s).</p>}</section>
      <section className="side-card rules"><h3>How to play</h3><div><span>01</span> Create a room or join with a code.</div><div><span>02</span> Wait for your turn.</div><div><span>03</span> Start with the last letter shown.</div><div><span>04</span> Longer words earn more points.</div></section><section className="mini-tip"><Sparkles size={17}/><p><b>Quick tip</b><br/>Think of words that end with common letters to keep the chain going.</p></section></aside></div>
    </section>}
    <footer><span>WORDCHAIN © 2026</span><span>Built for curious minds <span className="heart">♥</span></span></footer>
  </main>
}
