{ stdenv
, lib
, fetchzip
, autoPatchelfHook
, libxcrypt-legacy
}:

let
  arch = if stdenv.hostPlatform.isAarch64 then "aarch64" else "x86_64";
  platform = "${arch}-unknown-linux-gnu";
in
stdenv.mkDerivation rec {
  pname = "nav";
  version = "1.2.1";

  src = fetchzip {
    url = "https://github.com/Jojo4GH/nav/releases/download/v${version}/nav-${platform}.tar.gz";
    sha256 = {
      x86_64-linux = "sha256-ihn5wlagmujHlSfJpgojQNqa4NjLF1wk2pt8wHi60DY=";
      aarch64-linux = "sha256-l3rKu3OU/TUUjmx3p06k9V5eN3ZDNcxbxObLqVQ2B7U=";
    }.${stdenv.hostPlatform.system} or (throw "unsupported system ${stdenv.hostPlatform.system}");
  };

  nativeBuildInputs = [ autoPatchelfHook ];
  buildInputs = [ stdenv.cc.cc libxcrypt-legacy ];

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin
    cp nav $out/bin

    runHook postInstall
  '';

  meta = with lib;{
    description = "The interactive and stylish replacement for ls & cd!";
    homepage = "https://github.com/Jojo4GH/nav";
    license = licenses.mit;
    maintainers = with maintainers; [ David-Kopczynski ];
    platforms = [ "aarch64-linux" "x86_64-linux" ];
    mainProgram = pname;
  };
}